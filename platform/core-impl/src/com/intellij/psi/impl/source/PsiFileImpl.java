/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.*;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.text.BlockSupportImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedWeakReference;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Array;
import java.util.*;

public abstract class PsiFileImpl extends ElementBase implements PsiFileEx, PsiFileWithStubSupport, Queryable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiFileImpl");
  public static final String STUB_PSI_MISMATCH = "stub-psi mismatch";
  private static final AtomicFieldUpdater<PsiFileImpl, FileTrees> ourTreeUpdater =
    AtomicFieldUpdater.forFieldOfType(PsiFileImpl.class, FileTrees.class);

  private IElementType myElementType;
  protected IElementType myContentElementType;
  private long myModificationStamp;

  protected PsiFile myOriginalFile;
  private final FileViewProvider myViewProvider;
  private volatile FileTrees myTrees = FileTrees.noStub(null, this);
  private boolean myInvalidated;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private AstPathPsiMap myRefToPsi;
  private final ThreadLocal<FileElement> myFileElementBeingLoaded = new ThreadLocal<>();
  protected final PsiManagerEx myManager;
  public static final Key<Boolean> BUILDING_STUB = new Key<>("Don't use stubs mark!");
  private final PsiLock myPsiLock;

  protected PsiFileImpl(@NotNull IElementType elementType, IElementType contentElementType, @NotNull FileViewProvider provider) {
    this(provider);
    init(elementType, contentElementType);
  }

  protected PsiFileImpl(@NotNull FileViewProvider provider ) {
    myManager = (PsiManagerEx)provider.getManager();
    myViewProvider = provider;
    myRefToPsi = new AstPathPsiMap(getProject());
    myPsiLock = ((AbstractFileViewProvider) provider).getFilePsiLock();
  }

  public void setContentElementType(final IElementType contentElementType) {
    LOG.assertTrue(contentElementType instanceof ILazyParseableElementType, contentElementType);
    myContentElementType = contentElementType;
  }

  public IElementType getContentElementType() {
    return myContentElementType;
  }

  protected void init(@NotNull final IElementType elementType, final IElementType contentElementType) {
    myElementType = elementType;
    setContentElementType(contentElementType);
  }

  public TreeElement createContentLeafElement(CharSequence leafText) {
    if (myContentElementType instanceof ILazyParseableElementType) {
      return ASTFactory.lazy((ILazyParseableElementType)myContentElementType, leafText);
    }
    return ASTFactory.leaf(myContentElementType, leafText);
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Nullable
  public FileElement getTreeElement() {
    FileElement node = derefTreeElement();
    if (node != null) return node;

    if (!getViewProvider().isPhysical()) {
      return loadTreeElement();
    }

    return null;
  }

  protected FileElement derefTreeElement() {
    return myTrees.derefTreeElement();
  }

  @Override
  public VirtualFile getVirtualFile() {
    return getViewProvider().isEventSystemEnabled() ? getViewProvider().getVirtualFile() : null;
  }

  @Override
  public boolean processChildren(final PsiElementProcessor<PsiFileSystemItem> processor) {
    return true;
  }

  @Override
  public boolean isValid() {
    if (myManager.getProject().isDisposed()) {
      // normally FileManager.dispose would call markInvalidated
      // but there's temporary disposed project in tests, which doesn't actually dispose its components :(
      return false;
    }
    if (!myViewProvider.getVirtualFile().isValid()) {
      // PSI listeners receive VFS deletion events and do markInvalidated
      // but some VFS listeners receive the same events before that and ask PsiFile.isValid
      return false;
    }
    return !myInvalidated;
  }

  @Override
  public void markInvalidated() {
    myInvalidated = true;
    DebugUtil.onInvalidated(this);
  }

  @Override
  public boolean isContentsLoaded() {
    return derefTreeElement() != null;
  }

  @NotNull
  private FileElement loadTreeElement() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final FileViewProvider viewProvider = getViewProvider();
    if (viewProvider.isPhysical() && myManager.isAssertOnFileLoading(viewProvider.getVirtualFile())) {
      LOG.error("Access to tree elements not allowed in tests. path='" + viewProvider.getVirtualFile().getPresentableUrl()+"'");
    }

    FileElement treeElement = createFileElement(viewProvider.getContents());
    treeElement.setPsi(this);

    myFileElementBeingLoaded.set(treeElement);
    try {
      while (true) {
        FileTrees trees = myTrees;
        List<Pair<StubBasedPsiElementBase, AstPath>> bindings = calcStubAstBindings(treeElement, trees);

        FileElement savedTree = ensureTreeElement(viewProvider, treeElement, trees, bindings);
        if (savedTree != null) {
          return savedTree;
        }
      }
    }
    finally {
      myFileElementBeingLoaded.remove();
    }
  }

  @Nullable
  private FileElement ensureTreeElement(@NotNull FileViewProvider viewProvider,
                                        @NotNull FileElement treeElement,
                                        @NotNull FileTrees trees,
                                        @NotNull List<Pair<StubBasedPsiElementBase, AstPath>> bindings) {
    synchronized (myPsiLock) {
      FileElement existing = derefTreeElement();
      if (existing != null) {
        return existing;
      }

      if (trees != myTrees) {
        return null; // try again
      }

      switchFromStubToAst(bindings, trees);
      updateTrees(trees.withAst(createTreeElementPointer(treeElement)));

      if (LOG.isDebugEnabled() && viewProvider.isPhysical()) {
        LOG.debug("Loaded text for file " + viewProvider.getVirtualFile().getPresentableUrl());
      }

      return treeElement;
    }
  }

  @Override
  public ASTNode findTreeForStub(StubTree tree, StubElement<?> stub) {
    final Iterator<StubElement<?>> stubs = tree.getPlainList().iterator();
    final StubElement<?> root = stubs.next();
    final CompositeElement ast = calcTreeElement();
    if (root == stub) return ast;

    return findTreeForStub(ast, stubs, stub);
  }

  @Nullable
  private static ASTNode findTreeForStub(ASTNode tree, final Iterator<StubElement<?>> stubs, final StubElement stub) {
    final IElementType type = tree.getElementType();

    if (type instanceof IStubElementType && ((IStubElementType) type).shouldCreateStub(tree)) {
      final StubElement curStub = stubs.next();
      if (curStub == stub) return tree;
    }

    for (ASTNode node : tree.getChildren(null)) {
      final ASTNode treeForStub = findTreeForStub(node, stubs, stub);
      if (treeForStub != null) return treeForStub;
    }

    return null;
  }

  private void switchFromStubToAst(List<Pair<StubBasedPsiElementBase, AstPath>> bindings, FileTrees trees) {
    if (!bindings.isEmpty() && trees.useStrongRefs) {
      List<String> psiStrings = ContainerUtil.map(bindings, pair -> pair.first.getClass().getName());
      LOG.error(this + " of " + getClass() + "; " + psiStrings);
    }

    for (int i = 0; i < bindings.size(); i++) {
      Pair<StubBasedPsiElementBase, AstPath> pair = bindings.get(i);
      StubBasedPsiElementBase psi = pair.first;
      AstPath path = pair.second;
      path.getNode().setPsi(psi);
      myRefToPsi.cachePsi(path, psi);
      psi.setStubIndex(i + 1);
    }
  }

  private List<Pair<StubBasedPsiElementBase, AstPath>> calcStubAstBindings(@NotNull FileElement root, FileTrees trees) {
    final StubTree stubTree = trees.derefStub();
    if (stubTree == null || trees.astLoaded) { // don't bind green stub to AST: the PSI should already be cached in myRefToPsi
      return Collections.emptyList();
    }

    try {
      List<Pair<StubBase, TreeElement>> result = TreeUtil.calcStubAstBindings(stubTree, root);
      synchronized (myPsiLock) {
        return ContainerUtil.map(result, pair -> {
          StubElement stub = pair.first;
          PsiElement psi = stub.getPsi();
          assert psi != null : "Stub " + stub + " (" + stub.getClass() + ") has returned null PSI";
          AstPath path = AstPath.getNodePath((CompositeElement)pair.second);
          assert path != null : "Null path";
          return Pair.create((StubBasedPsiElementBase)psi, path);
        });
      }
    }
    catch (TreeUtil.StubBindingException e) {
      reportStubAstMismatch(e.getMessage(), stubTree);
      return Collections.emptyList();
    }
  }

  @Nullable
  public IStubFileElementType getElementTypeForStubBuilder() {
    ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(getLanguage());
    IFileElementType type = definition == null ? null : definition.getFileNodeType();
    return type instanceof IStubFileElementType ? (IStubFileElementType)type : null;
  }

  void reportStubAstMismatch(String message, StubTree stubTree) {
    rebuildStub();
    updateTrees(myTrees.clearStub(STUB_PSI_MISMATCH));

    throw StubTreeLoader.getInstance().stubTreeAndIndexDoNotMatch(message, stubTree, this);
  }

  @NotNull
  protected FileElement createFileElement(CharSequence docText) {
    final FileElement treeElement;
    final TreeElement contentLeaf = createContentLeafElement(docText);

    if (contentLeaf instanceof FileElement) {
      treeElement = (FileElement)contentLeaf;
    }
    else {
      final CompositeElement xxx = ASTFactory.composite(myElementType);
      assert xxx instanceof FileElement : "BUMM";
      treeElement = (FileElement)xxx;
      treeElement.rawAddChildrenWithoutNotifications(contentLeaf);
    }

    return treeElement;
  }

  @Override
  public void clearCaches() {
    myModificationStamp ++;
  }

  @Override
  public String getText() {
    final ASTNode tree = derefTreeElement();
    if (!isValid()) {
      // even invalid PSI can calculate its text by concatenating its children
      if (tree != null) return tree.getText();

      throw new PsiInvalidElementAccessException(this);
    }
    String string = getViewProvider().getContents().toString();
    if (tree != null && string.length() != tree.getTextLength()) {
      throw new AssertionError("File text mismatch: tree.length=" + tree.getTextLength() +
                               "; psi.length=" + string.length() +
                               "; this=" + this +
                               "; vp=" + getViewProvider());
    }
    return string;
  }

  @Override
  public int getTextLength() {
    final ASTNode tree = derefTreeElement();
    if (tree != null) return tree.getTextLength();

    PsiUtilCore.ensureValid(this);
    return getViewProvider().getContents().length();
  }

  @Override
  public TextRange getTextRange() {
    return new TextRange(0, getTextLength());
  }

  @Override
  public PsiElement getNextSibling() {
    return SharedPsiElementImplUtil.getNextSibling(this);
  }

  @Override
  public PsiElement getPrevSibling() {
    return SharedPsiElementImplUtil.getPrevSibling(this);
  }

  @Override
  public long getModificationStamp() {
    PsiElement context = getContext();
    PsiFile contextFile = context == null || !context.isValid() ? null : context.getContainingFile();
    long contextStamp = contextFile == null ? 0 : contextFile.getModificationStamp();
    return myModificationStamp + contextStamp;
  }

  @Override
  public void subtreeChanged() {
    doClearCaches("subtreeChanged");
    getViewProvider().rootChanged(this);
  }

  private void doClearCaches(String reason) {
    final FileElement tree = getTreeElement();
    if (tree != null) {
      tree.clearCaches();
    }

    synchronized (myPsiLock) {
      updateTrees(myTrees.clearStub(reason));
    }
    clearCaches();
  }

  @Override
  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException", "CloneDoesntCallSuperClone"})
  protected PsiFileImpl clone() {
    FileViewProvider viewProvider = getViewProvider();
    FileViewProvider providerCopy = viewProvider.clone();
    final Language language = getLanguage();
    if (providerCopy == null) {
      throw new AssertionError("Unable to clone the view provider: " + viewProvider + "; " + language);
    }
    PsiFileImpl clone = BlockSupportImpl.getFileCopy(this, providerCopy);
    copyCopyableDataTo(clone);

    clone.myRefToPsi = new AstPathPsiMap(getProject());
    if (getTreeElement() != null) {
      // not set by provider in clone
      final FileElement treeClone = (FileElement)calcTreeElement().clone();
      clone.setTreeElementPointer(treeClone); // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
      treeClone.setPsi(clone);
    }

    if (viewProvider.isEventSystemEnabled()) {
      clone.myOriginalFile = this;
    }
    else if (myOriginalFile != null) {
      clone.myOriginalFile = myOriginalFile;
    }

    FileManagerImpl.clearPsiCaches(providerCopy);

    return clone;
  }

  @Override
  @NotNull public String getName() {
    return getViewProvider().getVirtualFile().getName();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);
    doClearCaches("setName");
    return PsiFileImplUtil.setName(this, name);
  }

  @Override
  public void checkSetName(String name) {
    if (!getViewProvider().isEventSystemEnabled()) return;
    PsiFileImplUtil.checkSetName(this, name);
  }

  @Override
  public boolean isWritable() {
    return getViewProvider().getVirtualFile().isWritable();
  }

  @Override
  public PsiDirectory getParent() {
    return getContainingDirectory();
  }

  @Override
  @Nullable
  public PsiDirectory getContainingDirectory() {
    VirtualFile file = getViewProvider().getVirtualFile();
    final VirtualFile parentFile = file.getParent();
    if (parentFile == null) return null;
    if (!parentFile.isValid()) {
      LOG.error("Invalid parent: " + parentFile + " of file " + file + ", file.valid=" + file.isValid());
      return null;
    }
    return getManager().findDirectory(parentFile);
  }

  @Override
  @NotNull
  public PsiFile getContainingFile() {
    return this;
  }

  @Override
  public void delete() throws IncorrectOperationException {
    checkDelete();
    PsiFileImplUtil.doDelete(this);
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    if (!getViewProvider().isEventSystemEnabled()) {
      throw new IncorrectOperationException();
    }
    CheckUtil.checkWritable(this);
  }

  @Override
  @NotNull
  public PsiFile getOriginalFile() {
    return myOriginalFile == null ? this : myOriginalFile;
  }

  public void setOriginalFile(@NotNull final PsiFile originalFile) {
    myOriginalFile = originalFile.getOriginalFile();

    FileViewProvider original = myOriginalFile.getViewProvider();
    ((AbstractFileViewProvider)original).registerAsCopy((AbstractFileViewProvider)myViewProvider);
  }

  @Override
  @NotNull
  public PsiFile[] getPsiRoots() {
    final FileViewProvider viewProvider = getViewProvider();
    final Set<Language> languages = viewProvider.getLanguages();

    final PsiFile[] roots = new PsiFile[languages.size()];
    int i = 0;
    for (Language language : languages) {
      PsiFile psi = viewProvider.getPsi(language);
      if (psi == null) {
        LOG.error("PSI is null for "+language+"; in file: "+this);
      }
      roots[i++] = psi;
    }
    if (roots.length > 1) {
      Arrays.sort(roots, FILE_BY_LANGUAGE_ID);
    }
    return roots;
  }
  private static final Comparator<PsiFile> FILE_BY_LANGUAGE_ID = Comparator.comparing(o -> o.getLanguage().getID());

  @Override
  public boolean isPhysical() {
    // TODO[ik] remove this shit with dummy file system
    return getViewProvider().isEventSystemEnabled();
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return myElementType.getLanguage();
  }

  @Override
  @NotNull
  public FileViewProvider getViewProvider() {
    return myViewProvider;
  }

  public void setTreeElementPointer(@Nullable FileElement element) {
    updateTrees(FileTrees.noStub(element, this));
  }

  @Override
  public PsiElement findElementAt(int offset) {
    return getViewProvider().findElementAt(offset);
  }

  @Override
  public PsiReference findReferenceAt(int offset) {
    return getViewProvider().findReferenceAt(offset);
  }

  @Override
  @NotNull
  public char[] textToCharArray() {
    return CharArrayUtil.fromSequence(getViewProvider().getContents());
  }

  @SuppressWarnings("unchecked")
  @NotNull
  public <T> T[] findChildrenByClass(Class<T> aClass) {
    List<T> result = new ArrayList<>();
    for (PsiElement child : getChildren()) {
      if (aClass.isInstance(child)) result.add((T)child);
    }
    return result.toArray((T[]) Array.newInstance(aClass, result.size()));
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T> T findChildByClass(Class<T> aClass) {
    for (PsiElement child : getChildren()) {
      if (aClass.isInstance(child)) return (T)child;
    }
    return null;
  }

  public boolean isTemplateDataFile() {
    return false;
  }

  @Override
  public PsiElement getContext() {
    return FileContextUtil.getFileContext(this);
  }

  @Override
  public void onContentReload() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    DebugUtil.startPsiModification("onContentReload");
    try {
      myRefToPsi.invalidatePsi();

      FileElement treeElement = derefTreeElement();
      if (treeElement != null) {
        treeElement.detachFromFile();
        DebugUtil.onInvalidated(treeElement);
      }
      updateTrees(myTrees.clearStub("onContentReload"));
      setTreeElementPointer(null);
    }
    finally {
      DebugUtil.finishPsiModification();
    }
    clearCaches();
  }

  /**
   * @return a root stub of {@link #getStubTree()}, or null if the file is not stub-based or AST has been loaded.
   */
  @Nullable
  public StubElement getStub() {
    StubTree stubHolder = getStubTree();
    return stubHolder != null ? stubHolder.getRoot() : null;
  }

  /**
   * A green stub is a stub object that can co-exist with tree (AST). So, contrary to {@link #getStub()}, can be non-null
   * even if the AST has been loaded in this file. It can be used in cases when retrieving information from a stub is cheaper
   * than from AST.
   * @return a stub object corresponding to the file's content, or null if it's not available (e.g. has been garbage-collected)
   * @see #getStub()
   * @see #getStubTree()
   */
  @Nullable
  public final StubElement getGreenStub() {
    StubTree stubHolder = getGreenStubTree();
    return stubHolder != null ? stubHolder.getRoot() : null;
  }

  /**
   * @return a stub tree, if this file has it, and only if AST isn't loaded
   */
  @Override
  @Nullable
  public StubTree getStubTree() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (myTrees.astLoaded && !mayReloadStub()) return null;
    if (Boolean.TRUE.equals(getUserData(BUILDING_STUB))) return null;

    final StubTree derefd = derefStub();
    if (derefd != null) return derefd;

    if (getElementTypeForStubBuilder() == null) return null;

    final VirtualFile vFile = getVirtualFile();
    if (!(vFile instanceof VirtualFileWithId) || !vFile.isValid()) return null;

    ObjectStubTree tree = StubTreeLoader.getInstance().readOrBuild(getProject(), vFile, this);
    if (!(tree instanceof StubTree)) return null;
    final FileViewProvider viewProvider = getViewProvider();
    final List<Pair<IStubFileElementType, PsiFile>> roots = StubTreeBuilder.getStubbedRoots(viewProvider);

    synchronized (myPsiLock) {
      if (getTreeElement() != null || hasUnbindableCachedPsi()) return null;

      final StubTree derefdOnLock = derefStub();
      if (derefdOnLock != null) return derefdOnLock;

      PsiFileStub baseRoot = ((StubTree)tree).getRoot();
      if (baseRoot instanceof PsiFileStubImpl && !((PsiFileStubImpl)baseRoot).rootsAreSet()) {
        LOG.error("Stub roots must be set when stub tree was read or built with StubTreeLoader");
        return null;
      }
      final PsiFileStub[] stubRoots = baseRoot.getStubRoots();
      if (stubRoots.length != roots.size()) {
        final Function<PsiFileStub, String> stubToString = stub -> stub.getClass().getSimpleName();
        LOG.error("readOrBuilt roots = " + StringUtil.join(stubRoots, stubToString, ", ") + "; " +
                  StubTreeLoader.getFileViewProviderMismatchDiagnostics(viewProvider));
        rebuildStub();
        return null;
      }

      // first, set all references from stubs to existing PSI (in AST or AstPathPsiMap)
      Map<PsiFileImpl, StubTree> bindings = prepareAllStubTrees(roots, stubRoots);
      StubTree result = bindings.get(this);
      assert result != null : "Current file not in root list: " + roots + ", vp=" + viewProvider;

      // now stubs can be safely published
      for (PsiFileImpl eachPsiRoot : bindings.keySet()) {
        eachPsiRoot.updateTrees(eachPsiRoot.myTrees.withExclusiveStub(bindings.get(eachPsiRoot), bindings.keySet()));
      }
      return result;
    }
  }

  private static Map<PsiFileImpl, StubTree> prepareAllStubTrees(List<Pair<IStubFileElementType, PsiFile>> roots, PsiFileStub[] rootStubs) {
    Map<PsiFileImpl, StubTree> bindings = ContainerUtil.newIdentityHashMap();
    for (int i = 0; i < roots.size(); i++) {
      PsiFileImpl eachPsiRoot = (PsiFileImpl)roots.get(i).second;
      //noinspection unchecked
      ((StubBase)rootStubs[i]).setPsi(eachPsiRoot);
      StubTree stubTree = new StubTree(rootStubs[i]);
      FileElement fileElement = eachPsiRoot.getTreeElement();
      stubTree.setDebugInfo("created in getStubTree(), with AST = " + (fileElement != null));
      if (fileElement != null) {
        // Set references from these stubs to AST, because:
        // Stub index might call getStubTree on main PSI file, but then use getPlainListFromAllRoots and return stubs from another file.
        // Even if that file already has AST, stub.getPsi() should be the same as in AST
        TreeUtil.bindStubsToTree(stubTree, fileElement);
      } else {
        eachPsiRoot.bindStubsToCachedPsi(stubTree);
        bindings.put(eachPsiRoot, stubTree);
      }
    }
    return bindings;
  }

  private boolean mayReloadStub() {
    if (getTreeElement() != null || useStrongRefs()) {
      return false;
    }
    StubTreeLoader loader = StubTreeLoader.getInstance();
    if (loader != null && loader.isStubReloadingProhibited()) {
      return false;
    }
    return !hasUnbindableCachedPsi();
  }

  private boolean hasUnbindableCachedPsi() {
    for (PsiFile file : myViewProvider.getAllFiles()) {
      if (file instanceof PsiFileImpl) {
        for (StubBasedPsiElementBase<?> psi : ((PsiFileImpl)file).myRefToPsi.getAllCachedPsi()) {
          if (psi.getStubIndex() < 0) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Nullable
  private StubTree derefStub() {
    return myTrees.derefStub();
  }

  private void updateTrees(@NotNull FileTrees trees) {
    if (!ourTreeUpdater.compareAndSet(this, myTrees, trees)) {
      LOG.error("Non-atomic trees update");
      myTrees = trees;
    }
  }

  FileTrees getFileTrees() {
    return myTrees;
  }

  private void bindStubsToCachedPsi(StubTree stubTree) {
    for (StubBasedPsiElementBase<?> psi : myRefToPsi.getAllCachedPsi()) {
      int index = psi.getStubIndex();
      if (index >= 0) {
        //noinspection unchecked
        ((StubBase)stubTree.getPlainList().get(index)).setPsi(psi);
      }
    }
  }

  protected PsiFileImpl cloneImpl(FileElement treeElementClone) {
    PsiFileImpl clone = (PsiFileImpl)super.clone();
    clone.myRefToPsi = new AstPathPsiMap(getProject());
    clone.setTreeElementPointer(treeElementClone); // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
    treeElementClone.setPsi(clone);
    return clone;
  }

  private boolean isKeepTreeElementByHardReference() {
    return !getViewProvider().isEventSystemEnabled();
  }

  @NotNull
  private Getter<FileElement> createTreeElementPointer(@NotNull FileElement treeElement) {
    if (isKeepTreeElementByHardReference()) {
      return treeElement;
    }
    return myManager.isBatchFilesProcessingMode()
                 ? new PatchedWeakReference<>(treeElement)
                 : new SoftReference<>(treeElement);
  }

  @Override
  public final PsiManager getManager() {
    return myManager;
  }

  @Override
  public PsiElement getNavigationElement() {
    return this;
  }

  @Override
  public PsiElement getOriginalElement() {
    return getOriginalFile();
  }

  @NotNull
  public final FileElement calcTreeElement() {
    // Attempt to find (loaded) tree element without taking lock first.
    FileElement treeElement = getTreeElement();
    if (treeElement != null) return treeElement;

    treeElement = myFileElementBeingLoaded.get();
    if (treeElement != null) return treeElement;

    return loadTreeElement();
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return calcTreeElement().getChildrenAsPsiElements((TokenSet)null, PsiElement.ARRAY_FACTORY);
  }

  @Override
  public PsiElement getFirstChild() {
    return SharedImplUtil.getFirstChild(getNode());
  }

  @Override
  public PsiElement getLastChild() {
    return SharedImplUtil.getLastChild(getNode());
  }

  @Override
  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    SharedImplUtil.acceptChildren(visitor, getNode());
  }

  @Override
  public int getStartOffsetInParent() {
    return calcTreeElement().getStartOffsetInParent();
  }

  @Override
  public int getTextOffset() {
    return calcTreeElement().getTextOffset();
  }

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    return calcTreeElement().textMatches(text);
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return calcTreeElement().textMatches(element);
  }

  @Override
  public boolean textContains(char c) {
    return calcTreeElement().textContains(c);
  }

  @Override
  public final PsiElement copy() {
    return clone();
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    calcTreeElement().addInternal(elementCopy, elementCopy, null, null);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    calcTreeElement().addInternal(elementCopy, elementCopy, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    calcTreeElement().addInternal(elementCopy, elementCopy, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  @Override
  public final void checkAdd(@NotNull PsiElement element) {
    CheckUtil.checkWritable(this);
  }

  @Override
  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, null, null);
  }

  @Override
  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
  }

  @Override
  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    if (first == null) {
      LOG.assertTrue(last == null);
      return;
    }
    ASTNode firstElement = SourceTreeToPsiMap.psiElementToTree(first);
    ASTNode lastElement = SourceTreeToPsiMap.psiElementToTree(last);
    CompositeElement treeElement = calcTreeElement();
    LOG.assertTrue(firstElement.getTreeParent() == treeElement);
    LOG.assertTrue(lastElement.getTreeParent() == treeElement);
    CodeEditUtil.removeChildren(treeElement, firstElement, lastElement);
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    CompositeElement treeElement = calcTreeElement();
    return SharedImplUtil.doReplace(this, treeElement, newElement);
  }

  @Override
  public PsiReference getReference() {
    return null;
  }

  @Override
  @NotNull
  public PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return true;
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope() {
    return ResolveScopeManager.getElementResolveScope(this);
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return ResolveScopeManager.getElementUseScope(this);
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return getName();
      }

      @Override
      public String getLocationString() {
        final PsiDirectory psiDirectory = getParent();
        if (psiDirectory != null) {
          return psiDirectory.getVirtualFile().getPresentableUrl();
        }
        return null;
      }

      @Override
      public Icon getIcon(final boolean open) {
        return PsiFileImpl.this.getIcon(0);
      }
    };
  }

  @Override
  public void navigate(boolean requestFocus) {
    assert canNavigate() : this;
    //noinspection ConstantConditions
    PsiNavigationSupport.getInstance().getDescriptor(this).navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return PsiNavigationSupport.getInstance().canNavigate(this);
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Override
  @NotNull
  public final Project getProject() {
    return getManager().getProject();
  }

  @NotNull
  @Override
  public FileASTNode getNode() {
    return calcTreeElement();
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return this == another;
  }

  private final Object myStubFromTreeLock = new Object();

  /**
   * @return a stub tree object having {@link #getGreenStub()} as a root, or null if there's no green stub available
   */
  @Nullable
  public final StubTree getGreenStubTree() {
    StubTree result = derefStub();
    return result != null ? result : getStubTree();
  }

  @NotNull
  public StubTree calcStubTree() {
    StubTree tree = derefStub();
    if (tree != null) {
      return tree;
    }
    assert myFileElementBeingLoaded.get() == null : "non-empty thread-local";
    FileElement fileElement = calcTreeElement();
    synchronized (myStubFromTreeLock) {
      tree = derefStub();

      if (tree == null) {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        IStubFileElementType contentElementType = getElementTypeForStubBuilder();
        if (contentElementType == null) {
          VirtualFile vFile = getVirtualFile();
          String message = "ContentElementType: " + getContentElementType() + "; file: " + this +
                           "\n\t" + "Boolean.TRUE.equals(getUserData(BUILDING_STUB)) = " + Boolean.TRUE.equals(getUserData(BUILDING_STUB)) +
                           "\n\t" + "getTreeElement() = " + getTreeElement() +
                           "\n\t" + "vFile instanceof VirtualFileWithId = " + (vFile instanceof VirtualFileWithId) +
                           "\n\t" + "StubUpdatingIndex.canHaveStub(vFile) = " + StubTreeLoader.getInstance().canHaveStub(vFile);
          rebuildStub();
          throw new AssertionError(message);
        }

        StubElement currentStubTree = contentElementType.getBuilder().buildStubTree(this);
        if (currentStubTree == null) {
          throw new AssertionError("Stub tree wasn't built for " + contentElementType + "; file: " + this);
        }

        tree = new StubTree((PsiFileStub)currentStubTree);
        tree.setDebugInfo("created in calcStubTree");
        try {
          TreeUtil.bindStubsToTree(tree, fileElement);
        }
        catch (TreeUtil.StubBindingException e) {
          rebuildStub();
          throw new RuntimeException("Stub and PSI element type mismatch in " + getName(), e);
        }

        updateTrees(myTrees.withGreenStub(tree, this));
      }

      return tree;
    }
  }

  private void rebuildStub() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!myManager.isDisposed()) {
        myManager.dropPsiCaches();
      }

      final VirtualFile vFile = getVirtualFile();
      if (vFile != null && vFile.isValid()) {
        final Document doc = FileDocumentManager.getInstance().getCachedDocument(vFile);
        if (doc != null) {
          FileDocumentManager.getInstance().saveDocument(doc);
        }

        FileContentUtilCore.reparseFiles(vFile);
        StubTreeLoader.getInstance().rebuildStubTree(vFile);
      }
    }, ModalityState.NON_MODAL);
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    putInfo(this, info);
  }

  public static void putInfo(PsiFile psiFile, Map<String, String> info) {
    info.put("fileName", psiFile.getName());
    info.put("fileType", psiFile.getFileType().toString());
  }

  @Override
  public String toString() {
    return myElementType.toString();
  }

  public final void beforeAstChange() {
    checkWritable();
    if (!useStrongRefs()) {
      synchronized (myPsiLock) {
        for (PsiFile root : myViewProvider.getAllFiles()) {
          if ((root instanceof PsiFileImpl)) {
            ((PsiFileImpl)root).switchToStrongRefs();
          }
        }
      }
    }
  }

  private void checkWritable() {
    PsiDocumentManager docManager = PsiDocumentManager.getInstance(getProject());
    if (docManager instanceof PsiDocumentManagerBase && !((PsiDocumentManagerBase)docManager).isCommitInProgress()) {
      CheckUtil.checkWritable(this);
    }
  }

  private void switchToStrongRefs() {
    FileElement node = calcTreeElement();
    updateTrees(myTrees.switchToStrongRefs());
    myRefToPsi.switchToStrongRefs();
    AstPath.invalidatePaths(node);
  }

  @Nullable
  public StubBasedPsiElementBase<?> obtainPsi(@NotNull AstPath path, @NotNull Factory<StubBasedPsiElementBase<?>> creator) {
    if (useStrongRefs()) {
      return null;
    }

    StubBasedPsiElementBase<?> psi = myRefToPsi.getCachedPsi(path);
    if (psi != null) return psi;

    synchronized (myPsiLock) {
      if (useStrongRefs()) {
        return null;
      }

      psi = myRefToPsi.getCachedPsi(path);
      return psi != null ? psi : myRefToPsi.cachePsi(path, creator.create());
    }
  }

  final AstPathPsiMap getRefToPsi() {
    return myRefToPsi;
  }

  public final boolean useStrongRefs() {
    return myTrees.useStrongRefs;
  }

  public boolean mayCacheAst() {
    return myFileElementBeingLoaded.get() == null;
  }
}
