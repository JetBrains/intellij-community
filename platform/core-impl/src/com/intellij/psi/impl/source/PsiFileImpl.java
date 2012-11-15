/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ide.caches.FileContent;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.CacheUtil;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.reference.SoftReference;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import com.intellij.util.PatchedWeakReference;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.util.*;

public abstract class PsiFileImpl extends ElementBase implements PsiFileEx, PsiFileWithStubSupport, Queryable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiFileImpl");

  private IElementType myElementType;
  protected IElementType myContentElementType;
  private long myModificationStamp;

  protected PsiFile myOriginalFile = null;
  private final FileViewProvider myViewProvider;
  private static final Key<Document> HARD_REFERENCE_TO_DOCUMENT = new Key<Document>("HARD_REFERENCE_TO_DOCUMENT");
  private final Object myStubLock = new Object();
  private SoftReference<StubTree> myStub;
  protected final PsiManagerEx myManager;
  private volatile Object myTreeElementPointer; // SoftReference/WeakReference to ASTNode or a strong reference to a tree if the file is a DummyHolder
  public static final Key<Boolean> BUILDING_STUB = new Key<Boolean>("Don't use stubs mark!");

  protected PsiFileImpl(@NotNull IElementType elementType, IElementType contentElementType, @NotNull FileViewProvider provider) {
    this(provider);
    init(elementType, contentElementType);
  }

  protected PsiFileImpl(@NotNull FileViewProvider provider ) {
    myManager = (PsiManagerEx)provider.getManager();
    myViewProvider = provider;
  }

  public void setContentElementType(final IElementType contentElementType) {
    myContentElementType = contentElementType;
  }

  public IElementType getContentElementType() {
    return myContentElementType;
  }

  protected void init(@NotNull final IElementType elementType, final IElementType contentElementType) {
    myElementType = elementType;
    myContentElementType = contentElementType;
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

  public FileElement getTreeElement() {
    final FileElement noLockAttempt = (FileElement)_getTreeElement();
    if (noLockAttempt != null) return noLockAttempt;

    synchronized (myStubLock) {
      return getTreeElementNoLock();
    }
  }

  public FileElement getTreeElementNoLock() {
    ASTNode node = _getTreeElement();
    if (node == null && !getViewProvider().isPhysical()) {
      node = loadTreeElement();
    }
    return (FileElement)node;
  }

  protected boolean isKeepTreeElementByHardReference() {
    return !getViewProvider().isEventSystemEnabled();
  }

  private ASTNode _getTreeElement() {
    final Object pointer = myTreeElementPointer;
    if (pointer instanceof FileElement) {
      return (FileElement)pointer;
    }
    if (pointer instanceof Reference) {
      FileElement treeElement = (FileElement)((Reference)pointer).get();
      if (treeElement != null) return treeElement;

      synchronized (myStubLock) {
        if (myTreeElementPointer == pointer) {
          myTreeElementPointer = null;
        }
      }
    }
    return null;
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
    FileViewProvider provider = getViewProvider();
    final VirtualFile vFile = provider.getVirtualFile();
    if (!vFile.isValid()) return false;
    if (!provider.isEventSystemEnabled()) return true; // "dummy" file
    if (myManager.getProject().isDisposed()) return false;
    return isPsiUpToDate(vFile);
  }

  protected boolean isPsiUpToDate(@NotNull VirtualFile vFile) {
    final FileViewProvider provider = myManager.findViewProvider(vFile);
    Language language = getLanguage();
    if (provider == null || provider.getPsi(language) == this) { // provider == null in tests
      return true;
    }
    Language baseLanguage = provider.getBaseLanguage();
    return baseLanguage != language && provider.getPsi(baseLanguage) == this;
  }

  @Override
  public boolean isContentsLoaded() {
    return _getTreeElement() != null;
  }

  public FileElement loadTreeElement() {
    FileElement treeElement;

    synchronized (myStubLock) {
      treeElement = (FileElement)_getTreeElement();
      if (treeElement != null) {
        return treeElement;
      }

      final FileViewProvider viewProvider = getViewProvider();
      if (viewProvider.isPhysical() && myManager.isAssertOnFileLoading(viewProvider.getVirtualFile())) {
        LOG.error("Access to tree elements not allowed in tests. path='" + viewProvider.getVirtualFile().getPresentableUrl()+"'");
      }

      final Document document = viewProvider.isEventSystemEnabled() ? viewProvider.getDocument() : null;
      treeElement = createFileElement(viewProvider.getContents());
      if (document != null) {
        treeElement.putUserData(HARD_REFERENCE_TO_DOCUMENT, document);
      }
      treeElement.setPsi(this);

      StubTree stub = derefStub();

      if (stub != null) {
        final Iterator<StubElement<?>> stubs = stub.getPlainList().iterator();
        stubs.next(); // Skip file stub;
        synchronized (PsiLock.LOCK) {
          switchFromStubToAST(treeElement, stubs);
        }

        clearStub();
      }

      setTreeElement(treeElement);

      if (LOG.isDebugEnabled() && getViewProvider().isPhysical()) {
        LOG.debug("Loaded text for file " + getViewProvider().getVirtualFile().getPresentableUrl());
      }
    }

    if (getViewProvider().isEventSystemEnabled() && isPhysical()) {
      VirtualFile vFile = getViewProvider().getVirtualFile();
      final Document document = FileDocumentManager.getInstance().getCachedDocument(vFile);
      if (document != null) TextBlock.get(this).clear();
    }

    return treeElement;
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

  private void switchFromStubToAST(final ASTNode root, final Iterator<StubElement<?>> stubs) {
    final IElementType contentElementType = getContentElementType();
    if (!(contentElementType instanceof IStubFileElementType)) {
      final VirtualFile vFile = getVirtualFile();
      throw new AssertionError("A stub in a non-stub file '" + vFile +"'; isValid()=" + (vFile != null? vFile.isValid() : "null") +
                               " type: "+contentElementType+"; content:<<<\n"+
                               StringUtil.first(getViewProvider().getContents(),200,true)+
                               "\n>>>; stubs=" + ContainerUtil.collect(stubs));
    }
    final StubBuilder builder = ((IStubFileElementType)contentElementType).getBuilder();

    ((TreeElement)root).acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      protected void visitNode(TreeElement tree) {
        final IElementType type = tree.getElementType();
        final CompositeElement treeParent = tree.getTreeParent();
        if (treeParent != null && builder.skipChildProcessingWhenBuildingStubs(treeParent, type)) {
          return;
        }
        if (type instanceof IStubElementType && ((IStubElementType)type).shouldCreateStub(tree)) {
          if (!stubs.hasNext()) {
            rebuildStub();
            LOG.error("Stub list in " + getName() + " has fewer elements than PSI. Last AST element: " +
                      tree.getElementType() + " " + tree);
            stopWalking();
            return;
          }

          final StubElement stub = stubs.next();
          if (stub.getStubType() != tree.getElementType()) {
            rebuildStub();
            LOG.error("Stub and PSI element type mismatch in " + getName() + ": stub " + stub + ", AST " +
                      tree.getElementType() + "; " + tree);
            stopWalking();
            return;
          }

          PsiElement psi = stub.getPsi();
          assert psi != null : "Stub " + stub + " (" + stub.getClass() + ") has returned null PSI";
          ((CompositeElement)tree).setPsi(psi);
          StubBasedPsiElementBase<?> base = (StubBasedPsiElementBase)psi;
          base.setNode(tree);
          base.setStub(null);
        }
        super.visitNode(tree);
      }
    });
  }

  protected FileElement createFileElement(final CharSequence docText) {
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

    if (CacheUtil.isCopy(this)) {
      treeElement.setCharTable(IdentityCharTable.INSTANCE);
    }

    return treeElement;
  }

  public void unloadContent() {
    LOG.assertTrue(getTreeElement() != null);
    clearCaches();
    myViewProvider.beforeContentsSynchronized();
    setTreeElement(null);
    synchronized (myStubLock) {
      clearStub();
    }
  }

  private void clearStub() {
    StubTree stubHolder = myStub == null ? null : myStub.get();
    if (stubHolder != null) {
      ((StubBase<?>)stubHolder.getRoot()).setPsi(null);
    }
    myStub = null;
  }

  public void clearCaches() {
    myModificationStamp ++;
  }

  @Override
  public String getText() {
    return getViewProvider().getContents().toString();
  }

  @Override
  public int getTextLength() {
    final ASTNode tree = _getTreeElement();
    if (tree != null) return tree.getTextLength();

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
    return myModificationStamp;
  }

  @Override
  public void subtreeChanged() {
    doClearCaches();
    getViewProvider().rootChanged(this);
  }

  private void doClearCaches() {
    final FileElement tree = getTreeElement();
    if (tree != null) {
      tree.clearCaches();
    }

    synchronized (myStubLock) {
      clearStub();
      if (tree != null) {
        tree.putUserData(STUB_TREE_IN_PARSED_TREE, null);
      }
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
    PsiFileImpl clone = (PsiFileImpl)providerCopy.getPsi(language);
    if (clone == null) {
      throw new AssertionError("Cannot find psi file with: " + language + "." +
                               " Original viewprovider: " + viewProvider +
                               "; languages: " + viewProvider.getLanguages() +
                               "; copied viewprovider: " + providerCopy +
                               "; languages: " + providerCopy.getLanguages() +
                               "; Original virtual file: " + getVirtualFile() +
                               "; copied virtual file: " + providerCopy.getVirtualFile() +
                               "; its.getOriginal(): " +
                               (providerCopy.getVirtualFile() instanceof LightVirtualFile ? ((LightVirtualFile)providerCopy.getVirtualFile()).getOriginalFile() : null));
    }

    copyCopyableDataTo(clone);

    if (getTreeElement() != null) {
      // not set by provider in clone
      final FileElement treeClone = (FileElement)calcTreeElement().clone();
      clone.myTreeElementPointer = treeClone; // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
      treeClone.setPsi(clone);
    }

    if (viewProvider.isEventSystemEnabled()) {
      clone.myOriginalFile = this;
    }
    else if (myOriginalFile != null) {
      clone.myOriginalFile = myOriginalFile;
    }

    return clone;
  }

  @Override
  @NotNull public String getName() {
    return getViewProvider().getVirtualFile().getName();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);
    doClearCaches();
    return PsiFileImplUtil.setName(this, name);
  }

  @Override
  public void checkSetName(String name) throws IncorrectOperationException {
    if (!getViewProvider().isEventSystemEnabled()) return;
    PsiFileImplUtil.checkSetName(this, name);
  }

  @Override
  public boolean isWritable() {
    return getViewProvider().getVirtualFile().isWritable() && !CacheUtil.isCopy(this);
  }

  @Override
  public PsiDirectory getParent() {
    return getContainingDirectory();
  }

  @Override
  @Nullable
  public PsiDirectory getContainingDirectory() {
    final VirtualFile parentFile = getViewProvider().getVirtualFile().getParent();
    if (parentFile == null) return null;
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
  private static final Comparator<PsiFile> FILE_BY_LANGUAGE_ID = new Comparator<PsiFile>() {
    @Override
    public int compare(PsiFile o1, PsiFile o2) {
      return o1.getLanguage().getID().compareTo(o2.getLanguage().getID());
    }
  };

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

  public void setTreeElementPointer(FileElement element) {
    myTreeElementPointer = element;
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
    return CharArrayUtil.fromSequenceStrict(getViewProvider().getContents());
  }

  @NotNull
  public <T> T[] findChildrenByClass(Class<T> aClass) {
    List<T> result = new ArrayList<T>();
    for (PsiElement child : getChildren()) {
      if (aClass.isInstance(child)) result.add((T)child);
    }
    return result.toArray((T[]) Array.newInstance(aClass, result.size()));
  }

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
    subtreeChanged(); // important! otherwise cached information is not released
    if (isContentsLoaded()) {
      unloadContent();
    }
  }

  @Override
  public PsiFile cacheCopy(final FileContent content) {
    if (isContentsLoaded()) {
      return this;
    }
    else {
      CharSequence text;
      if (content == null) {
        Document document = FileDocumentManager.getInstance().getDocument(getVirtualFile());
        text = document.getCharsSequence();
      }
      else {
        text = CacheUtil.getContentText(content);
      }

      FileType fileType = getFileType();
      final String name = getName();
      PsiFile fileCopy =
        PsiFileFactory.getInstance(getProject()).createFileFromText(name, fileType, text, getModificationStamp(), false, false);
      fileCopy.putUserData(CacheUtil.CACHE_COPY_KEY, Boolean.TRUE);

      ((PsiFileImpl)fileCopy).setOriginalFile(this);
      return fileCopy;
    }
  }

  @Nullable
  public StubElement getStub() {
    StubTree stubHolder = getStubTree();
    return stubHolder != null ? stubHolder.getRoot() : null;
  }

  @Override
  @Nullable
  public StubTree getStubTree() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (Boolean.TRUE.equals(getUserData(BUILDING_STUB))) return null;

    final StubTree derefd = derefStub();
    if (derefd != null) return derefd;
    if (getTreeElementNoLock() != null) return null;

    final VirtualFile vFile = getVirtualFile();
    if (!(vFile instanceof VirtualFileWithId)) return null;

    ObjectStubTree tree = StubTreeLoader.getInstance().readOrBuild(getProject(), vFile, this);
    if (!(tree instanceof StubTree)) return null;
    StubTree stubHolder = (StubTree)tree;

    final IElementType contentElementType = getContentElementType();
    if (!(contentElementType instanceof IStubFileElementType)) {
      final FileViewProvider viewProvider = getViewProvider();
      throw new AssertionError("A stub in a non-stub file '" + vFile +"'; isValid()=" + vFile.isValid() + 
                               "; IndexStamp="+ StubTreeLoader.getInstance().getStubTreeTimestamp(vFile)  +
                               "; Type: " + contentElementType + "; " +
                               "Psi roots: " + viewProvider.getAllFiles() + "; " +
                               " StubUpdatingIndex.canHaveStub(vFile)=" + StubTreeLoader.getInstance().canHaveStub(vFile) +
                               " content:<<<\n"+
                               StringUtil.first(viewProvider.getContents(),200,true)+
                               "\n>>>; stubs=" + stubHolder.getPlainList());
    }

    synchronized (myStubLock) {
      if (getTreeElementNoLock() != null) return null;

      final StubTree derefdOnLock = derefStub();
      if (derefdOnLock != null) return derefdOnLock;

      myStub = new SoftReference<StubTree>(stubHolder);
      StubBase<PsiFile> base = (StubBase)stubHolder.getRoot();
      base.setPsi(this);
      return stubHolder;
    }
  }

  @Nullable
  private StubTree derefStub() {
    if (myStub == null) return  null;

    synchronized (myStubLock) {
      return myStub != null ? myStub.get() : null;
    }
  }

  protected PsiFileImpl cloneImpl(FileElement treeElementClone) {
    PsiFileImpl clone = (PsiFileImpl)super.clone();
    clone.myTreeElementPointer = treeElementClone; // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
    treeElementClone.setPsi(clone);
    return clone;
  }

  private void setTreeElement(ASTNode treeElement){
    Object newPointer;
    if (treeElement == null) {
      newPointer = null;
    }
    else if (isKeepTreeElementByHardReference()) {
      newPointer = treeElement;
    }
    else {
      newPointer = myManager.isBatchFilesProcessingMode()
                   ? new PatchedWeakReference<ASTNode>(treeElement)
                   : new PatchedSoftReference<ASTNode>(treeElement);
    }

    synchronized (myStubLock) {
      myTreeElementPointer = newPointer;
    }
  }

  public Object getStubLock() {
    return myStubLock;
  }

  @Override
  public PsiManager getManager() {
    return myManager;
  }

  @Override
  public PsiElement getNavigationElement() {
    return this;
  }

  @Override
  public PsiElement getOriginalElement() {
    return this;
  }

  public final FileElement calcTreeElement() {
    // Attempt to find (loaded) tree element without taking lock first.
    FileElement treeElement = getTreeElement();
    if (treeElement != null) return treeElement;

    synchronized (myStubLock) {
      treeElement = getTreeElement();
      if (treeElement != null) return treeElement;

      return loadTreeElement();
    }
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return calcTreeElement().getChildrenAsPsiElements((TokenSet)null, PsiElement.ARRAY_FACTORY);
  }

  @Override
  public PsiElement getFirstChild() {
    return SharedImplUtil.getFirstChild(calcTreeElement());
  }

  @Override
  public PsiElement getLastChild() {
    return SharedImplUtil.getLastChild(calcTreeElement());
  }

  @Override
  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    SharedImplUtil.acceptChildren(visitor, calcTreeElement());
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
  public final void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
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
  public Project getProject() {
    final PsiManager manager = getManager();
    if (manager == null) throw new PsiInvalidElementAccessException(this);

    return manager.getProject();
  }

  @Override
  public FileASTNode getNode() {
    return calcTreeElement();
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return this == another;
  }

  private static final Key<StubTree> STUB_TREE_IN_PARSED_TREE = new Key<StubTree>("STUB_TREE_IN_PARSED_TREE");

  public StubTree calcStubTree() {
    synchronized (myStubLock) {
      final FileElement fileElement = calcTreeElement();
      StubTree tree = fileElement.getUserData(STUB_TREE_IN_PARSED_TREE);
      if (tree == null) {
        IElementType contentElementType = getContentElementType();
        if (!(contentElementType instanceof IStubFileElementType)) {
          VirtualFile vFile = getVirtualFile();
          @NonNls String builder = "ContentElementType: " + contentElementType + "; file: " + this +
          "\n\t" + "Boolean.TRUE.equals(getUserData(BUILDING_STUB)) = " + Boolean.TRUE.equals(getUserData(BUILDING_STUB)) +
          "\n\t" + "getTreeElementNoLock() = " + getTreeElementNoLock() +
          "\n\t" + "vFile instanceof VirtualFileWithId = " + (vFile instanceof VirtualFileWithId) +
          "\n\t" + "StubUpdatingIndex.canHaveStub(vFile) = " + StubTreeLoader.getInstance().canHaveStub(vFile);
          LOG.error(builder);
        }
        final StubElement currentStubTree = ((IStubFileElementType)contentElementType).getBuilder().buildStubTree(this);
        tree = new StubTree((PsiFileStub)currentStubTree);
        bindFakeStubsToTree(tree);
        fileElement.putUserData(STUB_TREE_IN_PARSED_TREE, tree);
      }
      return tree;
    }
  }

  private void bindFakeStubsToTree(final StubTree stubTree) {
    final PsiFileImpl file = this;

    final Iterator<StubElement<?>> stubs = stubTree.getPlainList().iterator();
    stubs.next();  // skip file root stub
    final FileElement fileRoot = file.getTreeElement();
    assert fileRoot != null;

    bindStubs(fileRoot, stubs, ((IStubFileElementType)getContentElementType()).getBuilder());
  }

  private void bindStubs(final ASTNode tree, final Iterator<StubElement<?>> stubs, final StubBuilder builder) {
    ((TreeElement)tree).acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      protected void visitNode(TreeElement root) {
        CompositeElement parent = root.getTreeParent();
        IElementType parentType = parent == null ? null : parent.getElementType();
        final IElementType type = root.getElementType();
        if (parentType != null && builder.skipChildProcessingWhenBuildingStubs(parent, type)) {
          return;
        }

        if (type instanceof IStubElementType && ((IStubElementType) type).shouldCreateStub(root)) {
          final StubElement stub = stubs.hasNext() ? stubs.next() : null;
          if (stub == null || stub.getStubType() != type) {
            rebuildStub();
            assert false : "Stub and PSI element type mismatch in " + getName() + ": stub:" + stub + ", AST:" + type;
          }

          //noinspection unchecked
          ((StubBase)stub).setPsi(root.getPsi());
        }

        super.visitNode(root);
      }
    });
  }

  private void rebuildStub() {
    final VirtualFile vFile = getVirtualFile();

    if (vFile != null && vFile.isValid()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          final Document doc = FileDocumentManager.getInstance().getCachedDocument(vFile);
          if (doc != null) {
            FileDocumentManager.getInstance().saveDocument(doc);
          }
        }
      }, ModalityState.NON_MODAL);

      StubTreeLoader.getInstance().rebuildStubTree(vFile);
    }
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
}
