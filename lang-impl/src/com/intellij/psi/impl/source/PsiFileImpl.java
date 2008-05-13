package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDialect;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.impl.CacheUtil;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import com.intellij.util.PatchedWeakReference;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public abstract class PsiFileImpl extends ElementBase implements PsiFileEx, PsiFileWithStubSupport, PsiElement, Cloneable, NavigationItem {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiFileImpl");

  private IElementType myElementType;
  protected IElementType myContentElementType;

  protected PsiFile myOriginalFile = null;
  private final FileViewProvider myViewProvider;
  private static final Key<Document> HARD_REFERENCE_TO_DOCUMENT = new Key<Document>("HARD_REFERENCE_TO_DOCUMENT");
  private final Object myStubLock = PsiLock.LOCK;
  private WeakReference<StubTree> myStub;
  protected final PsiManagerEx myManager;
  protected volatile Object myTreeElementPointer; // SoftReference/WeakReference to RepositoryTreeElement when has repository id, RepositoryTreeElement otherwise

  protected PsiFileImpl(IElementType elementType, IElementType contentElementType, FileViewProvider provider) {
    this(provider);
    init(elementType, contentElementType);
  }

  protected PsiFileImpl( FileViewProvider provider ) {
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

  public TreeElement createContentLeafElement(final CharSequence text, final int startOffset, final int endOffset, final CharTable table) {
    return ASTFactory.leaf(myContentElementType, text, startOffset, endOffset, table);
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
    if (!getViewProvider().isPhysical()) return true; // "dummy" file
    final VirtualFile vFile = getViewProvider().getVirtualFile();
    return vFile.isValid() && isPsiUpToDate(vFile);
  }

  protected boolean isPsiUpToDate(VirtualFile vFile) {
    final FileViewProvider provider = myManager.findViewProvider(vFile);
    return provider.getPsi(getLanguage()) == this || provider.getPsi(provider.getBaseLanguage()) == this;
  }

  public boolean isContentsLoaded() {
    return _getTreeElement() != null;
  }

  public FileElement loadTreeElement() {
    synchronized (myStubLock) {
      FileElement treeElement = (FileElement)_getTreeElement();
      if (treeElement != null) return treeElement;

      final FileViewProvider viewProvider = getViewProvider();
      if (viewProvider.isPhysical() && myManager.isAssertOnFileLoading(viewProvider.getVirtualFile())) {
        LOG.error("Access to tree elements not allowed in tests." + viewProvider.getVirtualFile().getPresentableUrl());
      }

      // load document outside lock for better performance
      final Document document = viewProvider.isEventSystemEnabled() ? viewProvider.getDocument() : null;
      //synchronized (PsiLock.LOCK) {
      treeElement = createFileElement(viewProvider.getContents());
      if (document != null) {
        treeElement.putUserData(HARD_REFERENCE_TO_DOCUMENT, document);
      }
      setTreeElement(treeElement);
      treeElement.setPsi(this);
      //}

      StubTree stub = derefStub();
      if (stub != null) {
        final Iterator<StubElement<?>> stubs = stub.getPlainList().iterator();
        stubs.next(); // Skip file stub;
        switchFromStubToAST(treeElement, stubs);

        myStub = null;
      }

      if (getViewProvider().isEventSystemEnabled()) {
        ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myManager.getProject())).contentsLoaded(this);
      }
      if (LOG.isDebugEnabled() && getViewProvider().isPhysical()) {
        LOG.debug("Loaded text for file " + getViewProvider().getVirtualFile().getPresentableUrl());
      }

      return treeElement;
    }
  }

  public ASTNode findTreeForStub(StubTree tree, StubElement stub) {
    final Iterator<StubElement<?>> stubs = tree.getPlainList().iterator();
    stubs.next();
    return findTreeForStub(calcTreeElement(), stubs, stub);
  }

  private static ASTNode findTreeForStub(ASTNode tree, final Iterator<StubElement<?>> stubs, final StubElement stub) {
    if (tree instanceof ChameleonElement) {
      tree = ChameleonTransforming.transform((ChameleonElement)tree);
    }

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


  private void switchFromStubToAST(ASTNode tree, Iterator<StubElement<?>> stubs) {
    if (tree instanceof ChameleonElement) {
      tree = ChameleonTransforming.transform((ChameleonElement)tree);
    }

    final IElementType type = tree.getElementType();

    if (type instanceof IStubElementType && ((IStubElementType) type).shouldCreateStub(tree)) {
      final StubElement stub = stubs.next();
      if (stub.getStubType() != tree.getElementType()) {
        assert false: "Stub and PSI element type mismatch in " + getName() + ": stub " + stub + ", AST " + tree.getElementType();
      }
      final PsiElement psi = stub.getPsi();
      ((CompositeElement)tree).setPsi(psi);
      final StubBasedPsiElementBase<?> base = (StubBasedPsiElementBase)psi;
      base.setNode(tree);
      base.setStub(null);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Bound " + base + " to " + stub);
      }
    }

    for (ASTNode node : tree.getChildren(null)) {
      switchFromStubToAST(node, stubs);
    }
  }

  protected FileElement createFileElement(final CharSequence docText) {

    final CompositeElement xxx = ASTFactory.composite(myElementType);
    if (!(xxx instanceof FileElement)) {
      LOG.error("BUMM!");
    }
    final FileElement treeElement = (FileElement)xxx;
    if (CacheUtil.isCopy(this)) {
      treeElement.setCharTable(new IdentityCharTable());
    }

    TreeElement contentElement = createContentLeafElement(docText, 0, docText.length(), treeElement.getCharTable());
    TreeUtil.addChildren(treeElement, contentElement);
    return treeElement;
  }

  public void unloadContent() {
    LOG.assertTrue(getTreeElement() != null);
    clearCaches();
    myViewProvider.beforeContentsSynchronized();
    setTreeElement(null);
    myStub = null;
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
    final FileElement tree = getTreeElement();
    if (tree != null) {
      myTreeElementPointer = tree;
      tree.clearCaches();
    }

    synchronized (myStubLock) {
      final StubTree sTree = derefStub();
      if (sTree != null) {
        nullStubReferences(this);
        myStub = null;
      }
    }

    clearCaches();
    getViewProvider().rootChanged(this);
  }

  private static void nullStubReferences(final PsiElement psiElement) {
    if (psiElement instanceof StubBasedPsiElementBase) {
      ((StubBasedPsiElementBase<?>)psiElement).setStub(null);
    }

    for (PsiElement element : psiElement.getChildren()) {
      nullStubReferences(element);
    }
  }

  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException", "CloneDoesntCallSuperClone"})
  protected PsiFileImpl clone() {
    FileViewProvider provider = getViewProvider().clone();
    final LanguageDialect dialect = getLanguageDialect();
    PsiFileImpl clone = (PsiFileImpl)provider.getPsi(getLanguage());
    if (clone == null && dialect != null) {
      clone = (PsiFileImpl)provider.getPsi(dialect);
    }

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

    if (dialect != null) {
      clone.putUserData(PsiManagerImpl.LANGUAGE_DIALECT, dialect);
    }

    return clone;
  }

  @NotNull public String getName() {
    return getViewProvider().getVirtualFile().getName();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);
    subtreeChanged();
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

  public PsiFile getOriginalFile() {
    return myOriginalFile;
  }

  public void setOriginalFile(final PsiFile originalFile) {
    if (originalFile.getOriginalFile() != null) {
      myOriginalFile = originalFile.getOriginalFile();
    }
    else {
      myOriginalFile = originalFile;
    }
  }

  @NotNull
  public PsiFile[] getPsiRoots() {
    final FileViewProvider viewProvider = getViewProvider();
    final Set<Language> languages = viewProvider.getLanguages();
    final PsiFile[] roots = new PsiFile[languages.size()];
    int i = 0;
    for (Language language : languages) {
      roots[i++] = viewProvider.getPsi(language);
    }
    return roots;
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return getCopyableUserDataImpl(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    putCopyableUserDataImpl(key, value);
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

  @NonNls
  @Nullable
  public LanguageDialect getLanguageDialect() {
    return getUserData(PsiManagerImpl.LANGUAGE_DIALECT);
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
    synchronized (myStubLock) {
      StubTree stubHolder = derefStub();
      if (stubHolder == null) {
        if (getTreeElement() == null) {

          final VirtualFile vFile = getVirtualFile();
          stubHolder = StubTree.readFromVFile(vFile, getProject());
          if (stubHolder != null) {
            myStub = new WeakReference<StubTree>(stubHolder);
            ((PsiFileStubImpl)stubHolder.getRoot()).setPsi(this);
          }
        }
      }
      return stubHolder;
    }
  }

  private StubTree derefStub() {
    StubTree stubHolder = myStub != null ? myStub.get() : null;
    return stubHolder;
  }

  protected PsiFileImpl cloneImpl(FileElement treeElementClone) {
    PsiFileImpl clone = (PsiFileImpl)super.clone();
    clone.myTreeElementPointer = treeElementClone; // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
    treeElementClone.setPsi(clone);
    return clone;
  }

  public final void setTreeElement(ASTNode treeElement){
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
    CompositeElement treeElement = calcTreeElement();
    TreeElement childNode = treeElement.getFirstChildNode();

    TreeElement prevSibling = null;
    while (childNode != null) {
      if (childNode instanceof ChameleonElement) {
      TreeElement newChild = (TreeElement)childNode.getTransformedFirstOrSelf();
        if (newChild == null) {
          childNode = prevSibling == null ? treeElement.getFirstChildNode() : prevSibling.getTreeNext();
          continue;
        }
        childNode = newChild;
      }

      final PsiElement psi;
      if (childNode instanceof PsiElement) {
        psi = (PsiElement)childNode;
      }
      else {
        psi = childNode.getPsi();
      }
      psi.accept(visitor);

      prevSibling = childNode;
      childNode = childNode.getTreeNext();
    }
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
    return (PsiElement)clone();
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

  // Default implementation just to make sure it compiles.
  public ItemPresentation getPresentation() {
    return null;
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
}
