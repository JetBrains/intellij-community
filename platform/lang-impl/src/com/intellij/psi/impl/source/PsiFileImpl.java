/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.impl.CacheUtil;
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
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import com.intellij.util.PatchedWeakReference;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.text.CharArrayUtil;
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

  protected PsiFile myOriginalFile = null;
  private final FileViewProvider myViewProvider;
  private static final Key<Document> HARD_REFERENCE_TO_DOCUMENT = new Key<Document>("HARD_REFERENCE_TO_DOCUMENT");
  private final Object myStubLock = new String("file's stub lock");
  private SoftReference<StubTree> myStub;
  protected final PsiManagerEx myManager;
  private volatile Object myTreeElementPointer; // SoftReference/WeakReference to RepositoryTreeElement when has repository id, RepositoryTreeElement otherwise
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
    if (!getViewProvider().isPhysical() && _getTreeElement() == null) {
      setTreeElement(loadTreeElement());
    }
    return (FileElement)_getTreeElement();
  }

  protected boolean isKeepTreeElementByHardReference() {
    return !getViewProvider().isEventSystemEnabled();
  }

  private ASTNode _getTreeElement() {
    final Object pointer = myTreeElementPointer;
    if (pointer instanceof FileElement) {
      return (FileElement)pointer;
    }
    else if (pointer instanceof Reference) {
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



  public VirtualFile getVirtualFile() {
    return getViewProvider().isEventSystemEnabled() ? getViewProvider().getVirtualFile() : null;
  }

  public boolean processChildren(final PsiElementProcessor<PsiFileSystemItem> processor) {
    return true;
  }

  public boolean isValid() {
    final VirtualFile vFile = getViewProvider().getVirtualFile();
    if (!vFile.isValid()) return false;
    if (!getViewProvider().isPhysical()) return true; // "dummy" file
    return isPsiUpToDate(vFile);

    //FileViewProvider viewProvider = getViewProvider();
    //if (!viewProvider.isPhysical()) return true; // "dummy" file
    //final VirtualFile vFile = viewProvider.getVirtualFile();
    //if (!vFile.isValid() || !isPsiUpToDate(vFile)) return false;
    //PsiManager manager = getManager();
    //boolean valid = manager != null && !manager.getProject().isDisposed();
    //return valid;
  }

  protected boolean isPsiUpToDate(VirtualFile vFile) {
    final FileViewProvider provider = myManager.findViewProvider(vFile);
    return provider.getPsi(getLanguage()) == this || provider.getPsi(provider.getBaseLanguage()) == this;
  }

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
        switchFromStubToAST(treeElement, stubs);

        myStub = null;
      }

      setTreeElement(treeElement);

      if (LOG.isDebugEnabled() && getViewProvider().isPhysical()) {
        LOG.debug("Loaded text for file " + getViewProvider().getVirtualFile().getPresentableUrl());
      }
    }

    if (getViewProvider().isEventSystemEnabled()) {
      ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myManager.getProject())).contentsLoaded(this);
    }

    return treeElement;
  }

  public ASTNode findTreeForStub(StubTree tree, StubElement<?> stub) {
    final Iterator<StubElement<?>> stubs = tree.getPlainList().iterator();
    final StubElement<?> root = stubs.next();
    final CompositeElement ast = calcTreeElement();
    if (root == stub) return ast;

    return findTreeForStub(ast, stubs, stub);
  }

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


  private void switchFromStubToAST(ASTNode root, final Iterator<StubElement<?>> stubs) {
    ((TreeElement)root).acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      protected void visitNode(TreeElement tree) {
        final IElementType type = tree.getElementType();

        if (type instanceof IStubElementType && ((IStubElementType)type).shouldCreateStub(tree)) {
          if (!stubs.hasNext()) {
            LOG.error("Stub list in" +
                      this +
                      " " +
                      getName() +
                      " has fewer elements than PSI. Last AST element: " +
                      tree.getElementType() +
                      " " +
                      tree);
          }
          final StubElement stub = stubs.next();
          if (stub.getStubType() != tree.getElementType()) {
            rebuildStub();
            LOG.error("Stub and PSI element type mismatch in " +
                           getName() +
                           ": stub " +
                           stub +
                           ", AST " +
                           tree.getElementType() +
                           "; " +
                           tree);
          }
          final PsiElement psi = stub.getPsi();
          ((CompositeElement)tree).setPsi(psi);
          final StubBasedPsiElementBase<?> base = (StubBasedPsiElementBase)psi;
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
      treeElement.rawAddChildren(contentLeaf);
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
      myStub = null;
    }
  }

  public void clearCaches() {}

  public String getText() {
    return getViewProvider().getContents().toString();
  }

  public int getTextLength() {
    final ASTNode tree = _getTreeElement();
    if (tree != null) return tree.getTextLength();

    return getViewProvider().getContents().length();
  }

  public TextRange getTextRange() {
    return new TextRange(0, getTextLength());
  }

  public PsiElement getNextSibling() {
    return SharedPsiElementImplUtil.getNextSibling(this);
  }

  public PsiElement getPrevSibling() {
    return SharedPsiElementImplUtil.getPrevSibling(this);
  }

  public long getModificationStamp() {
    return getViewProvider().getModificationStamp();
  }

  public void subtreeChanged() {
    doClearCaches();
    getViewProvider().rootChanged(this);
  }

  private void doClearCaches() {
    final FileElement tree = getTreeElement();
    if (tree != null) {
      myTreeElementPointer = tree;
      tree.clearCaches();
    }

    synchronized (myStubLock) {
      myStub = null;
      if (tree != null) {
        tree.putUserData(STUB_TREE_IN_PARSED_TREE, null);
      }
    }

    clearCaches();
  }

  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException", "CloneDoesntCallSuperClone"})
  protected PsiFileImpl clone() {
    FileViewProvider provider = getViewProvider().clone();
    final Language language = getLanguage();
    if (provider == null) {
      throw new AssertionError("Unable to clone the view provider: " + getViewProvider() + "; " + language);
    }
    PsiFileImpl clone = (PsiFileImpl)provider.getPsi(language);
    assert clone != null:"Cannot find psi file with language:"+language + " from viewprovider:"+provider+" virtual file:"+getVirtualFile();

    copyCopyableDataTo(clone);

    if (getTreeElement() != null) {
      // not set by provider in clone
      final FileElement treeClone = (FileElement)calcTreeElement().clone();
      clone.myTreeElementPointer = treeClone; // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
      treeClone.setPsi(clone);
    }

    if (getViewProvider().isEventSystemEnabled()) {
      clone.myOriginalFile = this;
    }
    else if (myOriginalFile != null) {
      clone.myOriginalFile = myOriginalFile;
    }

    return clone;
  }

  @NotNull public String getName() {
    return getViewProvider().getVirtualFile().getName();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);
    doClearCaches();
    return PsiFileImplUtil.setName(this, name);
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    if (!getViewProvider().isEventSystemEnabled()) return;
    PsiFileImplUtil.checkSetName(this, name);
  }

  public boolean isWritable() {
    return getViewProvider().getVirtualFile().isWritable() && !CacheUtil.isCopy(this);
  }

  public PsiDirectory getParent() {
    return getContainingDirectory();
  }

  public PsiDirectory getContainingDirectory() {
    final VirtualFile parentFile = getViewProvider().getVirtualFile().getParent();
    if (parentFile == null) return null;
    return getManager().findDirectory(parentFile);
  }

  public PsiFile getContainingFile() {
    return this;
  }

  public void delete() throws IncorrectOperationException {
    checkDelete();
    PsiFileImplUtil.doDelete(this);
  }

  public void checkDelete() throws IncorrectOperationException {
    if (!getViewProvider().isEventSystemEnabled()) {
      throw new IncorrectOperationException();
    }
    CheckUtil.checkWritable(this);
  }

  @NotNull
  public PsiFile getOriginalFile() {
    return myOriginalFile == null ? this : myOriginalFile;
  }

  public void setOriginalFile(@NotNull final PsiFile originalFile) {
    myOriginalFile = originalFile.getOriginalFile();
  }

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
    return roots;
  }

  public boolean isPhysical() {
    // TODO[ik] remove this shit with dummy file system
    return getViewProvider().isEventSystemEnabled();
  }

  @NotNull
  public Language getLanguage() {
    return myElementType.getLanguage();
  }

  @NotNull
  public FileViewProvider getViewProvider() {
    return myViewProvider;
  }

  public void setTreeElementPointer(FileElement element) {
    myTreeElementPointer = element;
  }

  public PsiElement findElementAt(int offset) {
    return getViewProvider().findElementAt(offset);
  }

  public PsiReference findReferenceAt(int offset) {
    return getViewProvider().findReferenceAt(offset);
  }

  @NotNull
  public char[] textToCharArray() {
    return CharArrayUtil.fromSequenceStrict(getViewProvider().getContents());
  }

  @NotNull
  protected <T> T[] findChildrenByClass(Class<T> aClass) {
    List<T> result = new ArrayList<T>();
    for (PsiElement child : getChildren()) {
      if (aClass.isInstance(child)) result.add((T)child);
    }
    return result.toArray((T[]) Array.newInstance(aClass, result.size()));
  }

  @Nullable
  protected <T> T findChildByClass(Class<T> aClass) {
    for (PsiElement child : getChildren()) {
      if (aClass.isInstance(child)) return (T)child;
    }
    return null;
  }

  public boolean isTemplateDataFile() {
    return false;
  }

  public PsiElement getContext() {
    return FileContextUtil.getFileContext(this);
  }

  public void onContentReload() {
    subtreeChanged(); // important! otherwise cached information is not released
    if (isContentsLoaded()) {
      unloadContent();
    }
  }

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

  @Nullable
  public StubTree getStubTree() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (Boolean.TRUE.equals(getUserData(BUILDING_STUB))) return null;

    final StubTree derefd = derefStub();
    if (derefd != null) return derefd;
    if (getTreeElementNoLock() != null) return null;

    final VirtualFile vFile = getVirtualFile();
    if (!(vFile instanceof VirtualFileWithId)) return null;

    StubTree stubHolder = StubTree.readOrBuild(getProject(), vFile);
    if (stubHolder == null) return null;

    synchronized (myStubLock) {
      if (getTreeElementNoLock() != null) return null;

      final StubTree derefdOnLock = derefStub();
      if (derefdOnLock != null) return derefdOnLock;

      setStubTree(stubHolder);

      return derefStub();
    }
  }

  public void setStubTree(StubTree stubHolder) {
    synchronized (myStubLock) {
      assert getTreeElementNoLock() == null;
      myStub = new SoftReference<StubTree>(stubHolder);
      StubBase<PsiFile> base = (StubBase)stubHolder.getRoot();
      base.setPsi(this);

      base.putUserData(STUB_TREE_IN_PARSED_TREE, stubHolder); // This will prevent soft reference myStub to be collected before all of the stubs are collected.
    }
  }

  @Nullable
  private StubTree derefStub() {
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

  public PsiManager getManager() {
    return myManager;
  }

  public PsiElement getNavigationElement() {
    return this;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  public final CompositeElement calcTreeElement() {
    // Attempt to find (loaded) tree element without taking lock first.
    FileElement treeElement = getTreeElement();
    if (treeElement != null) return treeElement;

    synchronized (myStubLock) {
      treeElement = getTreeElement();
      if (treeElement != null) return treeElement;

      return loadTreeElement();
    }
  }


  @NotNull
  public PsiElement[] getChildren() {
    return calcTreeElement().getChildrenAsPsiElements(null, PsiElementArrayConstructor.PSI_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  public PsiElement getFirstChild() {
    return SharedImplUtil.getFirstChild(calcTreeElement());
  }

  public PsiElement getLastChild() {
    return SharedImplUtil.getLastChild(calcTreeElement());
  }

  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    SharedImplUtil.acceptChildren(visitor, calcTreeElement());
  }

  public int getStartOffsetInParent() {
    return calcTreeElement().getStartOffsetInParent();
  }
  public int getTextOffset() {
    return calcTreeElement().getTextOffset();
  }

  public boolean textMatches(@NotNull CharSequence text) {
    return calcTreeElement().textMatches(text);
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return calcTreeElement().textMatches(element);
  }

  public boolean textContains(char c) {
    return calcTreeElement().textContains(c);
  }

  public final PsiElement copy() {
    return clone();
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    calcTreeElement().addInternal(elementCopy, elementCopy, null, null);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    calcTreeElement().addInternal(elementCopy, elementCopy, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    calcTreeElement().addInternal(elementCopy, elementCopy, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public final void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, null, null);
  }

  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
  }

  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
  }

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

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    CompositeElement treeElement = calcTreeElement();
    LOG.assertTrue(treeElement.getTreeParent() != null);
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(newElement);
    treeElement.getTreeParent().replaceChildInternal(treeElement, elementCopy);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public PsiReference getReference() {
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return true;
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return ((PsiManagerEx)getManager()).getFileManager().getResolveScope(this);
  }

  @NotNull
  public SearchScope getUseScope() {
    return ((PsiManagerEx) getManager()).getFileManager().getUseScope(this);
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return getName();
      }

      public String getLocationString() {
        final PsiDirectory psiDirectory = getParent();
        if (psiDirectory != null) {
          return psiDirectory.getVirtualFile().getPresentableUrl();
        }
        return null;
      }

      public Icon getIcon(final boolean open) {
        return PsiFileImpl.this.getIcon(open ? ICON_FLAG_OPEN : ICON_FLAG_CLOSED);
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }

  public void navigate(boolean requestFocus) {
    EditSourceUtil.getDescriptor(this).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return EditSourceUtil.canNavigate(this);
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  public FileStatus getFileStatus() {
    if (!isPhysical()) return FileStatus.NOT_CHANGED;
    PsiFile contFile = getContainingFile();
    if (contFile == null) return FileStatus.NOT_CHANGED;
    VirtualFile vFile = contFile.getVirtualFile();
    return vFile != null ? FileStatusManager.getInstance(getProject()).getStatus(vFile) : FileStatus.NOT_CHANGED;
  }

  @NotNull
  public Project getProject() {
    final PsiManager manager = getManager();
    if (manager == null) throw new PsiInvalidElementAccessException(this);

    return manager.getProject();
  }

  public ASTNode getNode() {
    return calcTreeElement();
  }

  public boolean isEquivalentTo(final PsiElement another) {
    return this == another;
  }

  private static final Key<StubTree> STUB_TREE_IN_PARSED_TREE = new Key<StubTree>("STUB_TREE_IN_PARSED_TREE");

  public StubTree calcStubTree() {
    synchronized (myStubLock) {
      final FileElement fileElement = (FileElement)calcTreeElement();
      StubTree tree = fileElement.getUserData(STUB_TREE_IN_PARSED_TREE);
      if (tree == null) {
        final StubElement currentStubTree = ((IStubFileElementType)getContentElementType()).getBuilder().buildStubTree(this);
        tree = new StubTree((PsiFileStub)currentStubTree);
        bindFakeStubsToTree(tree);
        fileElement.putUserData(STUB_TREE_IN_PARSED_TREE, tree);
      }
      return tree;
    }
  }

  private void bindFakeStubsToTree(StubTree stubTree) {
    final PsiFileImpl file = this;

    final Iterator<StubElement<?>> stubs = stubTree.getPlainList().iterator();
    stubs.next(); // skip file root stub
    final FileElement fileRoot = file.getTreeElement();
    assert fileRoot != null;

    bindStubs(fileRoot, stubs);
  }

  @Nullable
  private StubElement bindStubs(ASTNode tree, Iterator<StubElement<?>> stubs) {
    final IElementType type = tree.getElementType();

    if (type instanceof IStubElementType && ((IStubElementType) type).shouldCreateStub(tree)) {
      final StubElement stub = stubs.next();
      if (stub.getStubType() != tree.getElementType()) {
        rebuildStub();

        assert false: "Stub and PSI element type mismatch:  stub " + stub + ", AST " + tree.getElementType();
      }

      ((StubBase)stub).setPsi(tree.getPsi());
    }

    for (ASTNode node : tree.getChildren(null)) {
      final StubElement res = bindStubs(node, stubs);
      if (res != null) {
        return res;
      }
    }

    return null;
  }

  private void rebuildStub() {
    final VirtualFile vFile = getVirtualFile();

    if (vFile != null && vFile.isValid()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          final Document doc = FileDocumentManager.getInstance().getCachedDocument(vFile);
          if (doc != null) {
            FileDocumentManager.getInstance().saveDocument(doc);
          }
        }
      }, ModalityState.NON_MODAL);

      FileBasedIndex.getInstance().requestReindex(vFile);
    }
  }

  public void putInfo(Map<String, String> info) {
    putInfo(this, info);
  }

  public static void putInfo(PsiFile psiFile, Map<String, String> info) {
    info.put("fileName", psiFile.getName());
    info.put("fileType", psiFile.getFileType().toString());
  }
}
