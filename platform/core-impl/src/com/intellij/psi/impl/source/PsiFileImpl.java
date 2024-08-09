// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.*;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.testFramework.ReadOnlyLightVirtualFile;
import com.intellij.util.*;
import com.intellij.util.indexing.IndexingDataKeys;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.util.ObjectUtils.tryCast;

public abstract class PsiFileImpl extends ElementBase implements PsiFileEx, PsiFileWithStubSupport, Queryable, Cloneable {
  private static final CharTable NON_INTERNING_CHAR_TABLE = new CharTable() {
    @Override
    public @NotNull CharSequence intern(@NotNull CharSequence text) {
      return text;
    }

    @Override
    public @NotNull CharSequence intern(@NotNull CharSequence baseText, int startOffset, int endOffset) {
      return new CharSequenceSubSequence(baseText, startOffset, endOffset);
    }
  };
  private static final Logger LOG = Logger.getInstance(PsiFileImpl.class);
  static final @NonNls String STUB_PSI_MISMATCH = "stub-psi mismatch";

  private IElementType myElementType;
  protected IElementType myContentElementType;
  private long myModificationStamp;

  protected PsiFile myOriginalFile;
  private final AbstractFileViewProvider myViewProvider;
  private volatile FileTrees myTrees = FileTrees.noStub(null, this);
  private volatile boolean myPossiblyInvalidated;
  protected final PsiManagerEx myManager;
  public static final Key<Boolean> BUILDING_STUB = new Key<>("Don't use stubs mark!");
  private final PsiLock myPsiLock;
  private volatile boolean myLoadingAst;

  protected PsiFileImpl(@NotNull IElementType elementType, IElementType contentElementType, @NotNull FileViewProvider provider) {
    this(provider);
    init(elementType, contentElementType);
  }

  protected PsiFileImpl(@NotNull FileViewProvider provider ) {
    myManager = (PsiManagerEx)provider.getManager();
    myViewProvider = (AbstractFileViewProvider)provider;
    myPsiLock = myViewProvider.getFilePsiLock();
  }

  public void setContentElementType(IElementType contentElementType) {
    LOG.assertTrue(contentElementType instanceof ILazyParseableElementType, contentElementType);
    myContentElementType = contentElementType;
  }

  public IElementType getContentElementType() {
    return myContentElementType;
  }

  protected void init(@NotNull IElementType elementType, IElementType contentElementType) {
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

  public @Nullable FileElement getTreeElement() {
    FileElement node = derefTreeElement();
    if (node != null) return node;

    if (!getViewProvider().isPhysical()) {
      return loadTreeElement();
    }

    return null;
  }

  FileElement derefTreeElement() {
    return myTrees.derefTreeElement();
  }

  @Override
  public VirtualFile getVirtualFile() {
    VirtualFile indexingFile = IndexingDataKeys.VIRTUAL_FILE.get(this);
    if (indexingFile != null) return indexingFile;
    return getViewProvider().isEventSystemEnabled() ? getViewProvider().getVirtualFile() : null;
  }

  @Override
  public boolean processChildren(@NotNull PsiElementProcessor<? super PsiFileSystemItem> processor) {
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

    if (!myPossiblyInvalidated) return true;

    /*
    Originally, all PSI was invalidated on root change, to avoid UI freeze (IDEA-172762),
    but that has led to too many PIEAEs (like IDEA-191185, IDEA-188292, IDEA-184186, EA-114990).

    Ideally those clients should all be converted to smart pointers, but that proved to be quite hard to do, especially without breaking API.
    And they mostly worked before those batch invalidations.

    So now we have a smarter way of dealing with this issue. On root change, we mark
    PSI as "potentially invalid", and then, when someone calls "isValid"
    (hopefully not for all cached PSI at once, and hopefully in a background thread),
    we check if the old PSI is equivalent to the one that would be re-created in its place.
    If yes, we return valid. If no, we invalidate the old PSI forever and return the new one.
    */

    // synchronized by read-write action
    if (((FileManagerImpl)myManager.getFileManager()).evaluateValidity(this)) {
      myPossiblyInvalidated = false;
      PsiInvalidElementAccessException.setInvalidationTrace(this, null);
      return true;
    }
    return false;
  }

  @Override
  public final void markInvalidated() {
    myPossiblyInvalidated = true;
    DebugUtil.onInvalidated(this);
  }

  @Override
  public boolean isContentsLoaded() {
    return derefTreeElement() != null;
  }

  protected void assertReadAccessAllowed() {
    if (myViewProvider.getVirtualFile() instanceof ReadOnlyLightVirtualFile) return;
    ApplicationManager.getApplication().assertReadAccessAllowed();
  }

  private @NotNull FileElement loadTreeElement() {
    assertReadAccessAllowed();

    if (myPossiblyInvalidated) {
      PsiUtilCore.ensureValid(this); // for invalidation trace diagnostics
    }

    FileViewProvider viewProvider = getViewProvider();
    if (viewProvider.isPhysical()) {
      VirtualFile vFile = viewProvider.getVirtualFile();
      AstLoadingFilter.assertTreeLoadingAllowed(vFile);
      if (myManager.isAssertOnFileLoading(vFile)) {
        reportProhibitedAstAccess(vFile);
      }
    }

    try {
      synchronized (myPsiLock) {
        FileElement treeElement = derefTreeElement();
        if (treeElement != null) {
          return treeElement;
        }

        treeElement = createFileElement(viewProvider.getContents());
        treeElement.setPsi(this);

        myLoadingAst = true;
        try {
          updateTrees(myTrees.withAst(createTreeElementPointer(treeElement)));
        }
        finally {
          myLoadingAst = false;
        }

        if (LOG.isDebugEnabled() && viewProvider.isPhysical()) {
          LOG.debug("Loaded text for file " + viewProvider.getVirtualFile().getPresentableUrl());
        }

        return treeElement;
      }
    }
    catch (StubTreeLoader.StubTreeAndIndexUnmatchCoarseException e) {
      throw e.createCompleteException();
    }
  }

  /**
   * Reports unexpected AST loading in tests.<p></p>
   *
   * AST loading is expensive and should be avoided for files that aren't already opened in the editor.
   * Resolving references during editor highlighting should be done via stubs (see {@link com.intellij.extapi.psi.StubBasedPsiElementBase})
   * or other indices, otherwise highlighting can become quite slow and memory-hungry due to parsing a lot of
   * other files and building their ASTs.<p></p>
   *
   * To help prevent this performance issue, there's a mode in tests when AST loading for non-opened files is prohibited.
   * To fix it, find in the stack trace where an AST-requiring API is called and consider using stubs or other indices instead.
   * In a rare case when loading AST is actually OK (e.g. during reference search for "unused symbol" highlighting),
   * you can switch off this check, e.g. via {@link com.intellij.testFramework.fixtures.CodeInsightTestFixture#allowTreeAccessForFile}.
   * <p></p>
   *
   * Note that this failure can be nondeterministic due to garbage collector which might or might not have collected previously loaded AST.
   * To make debugging simpler in this case, you can increase the chance of failure by starting the test with a smaller Xmx.
   */
  private static void reportProhibitedAstAccess(VirtualFile vFile) {
    LOG.error("Access to tree elements not allowed for '" + vFile.getPresentableUrl() + "'.\n" +
              "Try using stub-based PSI API to avoid expensive AST loading for files that aren't already opened in the editor.\n" +
              "Consult this method's javadoc for more details.");
  }

  @Override
  public @NotNull StubbedSpine getStubbedSpine() {
    return withGreenStubTreeOrAst(
      stubTree -> stubTree.getSpine(),
      ast -> {
        AstSpine astSpine = ast.getStubbedSpine();
        if (!myTrees.useSpineRefs()) {
          synchronized (myPsiLock) {
            updateTrees(myTrees.switchToSpineRefs(FileTrees.getAllSpinePsi(astSpine)));
          }
        }
        return astSpine;
      }
    );
  }

  public @Nullable IStubFileElementType<?> getElementTypeForStubBuilder() {
    ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(getLanguage());
    IFileElementType type = definition == null ? null : definition.getFileNodeType();
    return type instanceof IStubFileElementType ? (IStubFileElementType<?>)type : null;
  }

  protected @NotNull FileElement createFileElement(CharSequence docText) {
    FileElement treeElement;
    TreeElement contentLeaf = createContentLeafElement(docText);

    if (contentLeaf instanceof FileElement) {
      treeElement = (FileElement)contentLeaf;
      if (getUserData(IndexingDataKeys.VIRTUAL_FILE) != null) {
        treeElement.setCharTable(NON_INTERNING_CHAR_TABLE);
      }
    }
    else {
      CompositeElement xxx = ASTFactory.composite(myElementType);
      assert xxx instanceof FileElement : "BUMM";
      treeElement = (FileElement)xxx;
      treeElement.rawAddChildrenWithoutNotifications(contentLeaf);
    }

    return treeElement;
  }

  @Override
  public void clearCaches() {
    myModificationStamp++;
  }

  @Override
  public String getText() {
    ASTNode tree = derefTreeElement();
    if (!isValid()) {
      ProgressManager.checkCanceled();

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
    ASTNode tree = derefTreeElement();
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
    FileElement tree = getTreeElement();
    if (tree != null) {
      tree.clearCaches();
    }

    synchronized (myPsiLock) {
      if (myTrees.useSpineRefs()) {
        LOG.error("Somebody has requested stubbed spine during PSI operations; not only is this expensive, but will also cause stub PSI invalidation");
      }
      updateTrees(myTrees.clearStub("subtreeChanged"));
    }
    clearCaches();
    getViewProvider().rootChanged(this);
  }

  @Override
  @SuppressWarnings("CloneDoesntCallSuperClone")
  protected PsiFileImpl clone() {
    FileViewProvider viewProvider = getViewProvider();
    FileViewProvider providerCopy = viewProvider.clone();
    Language language = getLanguage();
    if (providerCopy == null) {
      throw new AssertionError("Unable to clone the view provider: " + viewProvider + "; " + language);
    }
    PsiFileImpl clone = BlockSupportImpl.getFileCopy(this, providerCopy);
    copyCopyableDataTo(clone);

    if (getTreeElement() != null) {
      // not set by provider in clone
      FileElement treeClone = (FileElement)calcTreeElement().clone();
      clone.setTreeElementPointer(treeClone); // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
      treeClone.setPsi(clone);
    }
    else {
      clone.setTreeElementPointer(null);
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
  public @NotNull String getName() {
    return getViewProvider().getVirtualFile().getName();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);
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
  public @Nullable PsiDirectory getContainingDirectory() {
    VirtualFile file = getViewProvider().getVirtualFile();
    VirtualFile parentFile = file.getParent();
    if (parentFile == null) return null;
    if (!parentFile.isValid()) {
      LOG.error("Invalid parent: " + parentFile + " of file " + file + ", file.valid=" + file.isValid());
      return null;
    }
    return getManager().findDirectory(parentFile);
  }

  @Override
  public @NotNull PsiFile getContainingFile() {
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
      if (PsiFileImplUtil.canDeleteNonPhysicalFile(this)) return;
      throw new IncorrectOperationException();
    }
    CheckUtil.checkWritable(this);
  }

  @Override
  public @NotNull PsiFile getOriginalFile() {
    return myOriginalFile == null ? this : myOriginalFile;
  }

  public void setOriginalFile(@NotNull PsiFile originalFile) {
    myOriginalFile = originalFile.getOriginalFile();

    FileViewProvider original = myOriginalFile.getViewProvider();
    ((AbstractFileViewProvider)original).registerAsCopy(myViewProvider);
  }

  @Override
  public PsiFile @NotNull [] getPsiRoots() {
    FileViewProvider viewProvider = getViewProvider();
    Set<Language> languages = viewProvider.getLanguages();

    PsiFile[] roots = new PsiFile[languages.size()];
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
    return getViewProvider().isEventSystemEnabled();
  }

  @Override
  public @NotNull Language getLanguage() {
    return myElementType.getLanguage();
  }

  @Override
  public @Nullable IFileElementType getFileElementType() {
    return myElementType instanceof IFileElementType ? (IFileElementType)myElementType
                                                     : tryCast(myContentElementType, IFileElementType.class);
  }

  @Override
  public @NotNull FileViewProvider getViewProvider() {
    return myViewProvider;
  }

  @Override
  public final @NotNull Document getFileDocument() {
    return PsiFileEx.super.getFileDocument();
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
  public char @NotNull [] textToCharArray() {
    return CharArrayUtil.fromSequence(getViewProvider().getContents());
  }

  public <T> T @NotNull [] findChildrenByClass(Class<T> aClass) {
    List<T> result = new ArrayList<>();
    for (PsiElement child : getChildren()) {
      if (aClass.isInstance(child)) {
        //noinspection unchecked
        result.add((T)child);
      }
    }
    return result.toArray(ArrayUtil.newArray(aClass, result.size()));
  }

  public @Nullable <T> T findChildByClass(Class<T> aClass) {
    for (PsiElement child : getChildren()) {
      if (aClass.isInstance(child)) {
        //noinspection unchecked
        return (T)child;
      }
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

    clearContent("onContentReload");
  }

  final void clearContent(String reason) {
    DebugUtil.performPsiModification(reason, () -> {
      synchronized (myPsiLock) {
        FileElement treeElement = derefTreeElement();
        if (treeElement != null) {
          treeElement.detachFromFile();
          DebugUtil.onInvalidated(treeElement);
        }
        updateTrees(myTrees.clearStub(reason));
        setTreeElementPointer(null);
      }
    });
    clearCaches();
  }

  /**
   * @return a root stub of {@link #getStubTree()}, or null if the file is not stub-based or AST has been loaded.
   */
  public @Nullable StubElement<?> getStub() {
    StubTree stubHolder = getStubTree();
    return stubHolder != null ? stubHolder.getRoot() : null;
  }

  /**
   * A green stub is a stub object that can co-exist with tree (AST). So, contrary to {@link #getStub()}, can be non-null
   * even if the AST has been loaded in this file. It can be used in cases when retrieving information from a stub is cheaper
   * than from AST.
   *
   * @return a stub object corresponding to the file's content, or null if it's not available (e.g. has been garbage-collected)
   * @see #getStub()
   * @see #getStubTree()
   *
   * @deprecated Use {@link #withGreenStubOrAst} to avoid race condition. Race condition can happen when AST is attached to
   *             the {@code PsiFile} when stub is being retrieved from the file system. In such case {@code getGreenStub} may
   *             return {@code null}. Between call to {@code getGreenStub} and access to the AST, the AST may be GC-ed, as it
   *             is only softly referenced. This will result in file reparse, which leads to performance degradation
   *             and especially to the hard-to-locate flakiness of tests.
   */
  @Deprecated
  public final @Nullable StubElement<?> getGreenStub() {
    StubTree stubHolder = getGreenStubTree();
    return stubHolder != null ? stubHolder.getRoot() : null;
  }

  /**
   * @return a stub tree object having {@link #getGreenStub()} as a root, or null if there's no green stub available
   * @deprecated Use {@link #getStubTreeOrFileElement()} or {@link #withGreenStubTreeOrAst(Function, Function)}.
   *             See deprecation note of {@link #getGreenStub()}
   */
  @Deprecated
  public final @Nullable StubTree getGreenStubTree() {
    return getStubTreeOrFileElement().first;
  }

  @SuppressWarnings("unchecked")
  public final <T> T withGreenStubOrAst(
    Function<PsiFileStub<?>, T> stubProcessor,
    Function<FileElement, T> astProcessor
  ) {
    //noinspection rawtypes
    return withGreenStubOrAst((Class<PsiFileStub<?>>)(Class)PsiFileStub.class, stubProcessor, astProcessor);
  }

  public final <T, S extends PsiFileStub<?>> T withGreenStubOrAst(
    Class<S> stubClass,
    Function<S, T> stubProcessor,
    Function<FileElement, T> astProcessor
  ) {
    Pair<@Nullable StubTree, @Nullable FileElement> result = getStubTreeOrFileElement();
    StubElement<?> stubElement = result.first != null ? result.first.getRoot() : null;
    if (stubElement != null && !stubClass.isAssignableFrom(stubElement.getClass())) {
      IStubFileElementType<?> elementType = getElementTypeForStubBuilder();
      String elementTypeName = elementType != null ? elementType.getExternalId() : null;
      Logger.getInstance(PsiFileImpl.class).error("stub: " + stubElement.getClass().getName() + "; file: " + elementTypeName);
      stubElement = null;
    }
    if (stubElement != null) {
      //noinspection unchecked
      return stubProcessor.apply((S)stubElement);
    }
    FileElement fileElement = result.second;
    if (fileElement == null) {
      fileElement = calcTreeElement();
    }
    return astProcessor.apply(fileElement);
  }

  public final <T> T withGreenStubTreeOrAst(
    Function<StubTree, T> stubProcessor,
    Function<FileElement, T> astProcessor
  ) {
    Pair<@Nullable StubTree, @Nullable FileElement> result = getStubTreeOrFileElement();
    if (result.first != null) {
      return stubProcessor.apply(result.first);
    }
    FileElement fileElement = result.second;
    if (fileElement == null) {
      fileElement = calcTreeElement();
    }
    return astProcessor.apply(fileElement);
  }


  /**
   * @return a stub tree, if this file has it, and only if AST isn't loaded
   * @implNote for non-physical files, this method can still load AST even if it's not yet loaded
   */
  @Override
  public @Nullable StubTree getStubTree() {
    Pair<StubTree, FileElement> result = getStubTreeOrFileElement();
    return result.second == null ? result.first : null;
  }

  public @NotNull Pair<@Nullable StubTree, @Nullable FileElement> getStubTreeOrFileElement() {
    assertReadAccessAllowed();

    StubTree derefd = derefStub();
    FileElement treeElement = getTreeElement();
    if (derefd != null || treeElement != null) {
      return Pair.create(derefd, treeElement);
    }

    if (Boolean.TRUE.equals(getUserData(BUILDING_STUB)) || myLoadingAst || getElementTypeForStubBuilder() == null) {
      return Pair.empty();
    }

    VirtualFile vFile = getVirtualFile();

    ObjectStubTree<?> tree = StubTreeLoader.getInstance().readOrBuild(getProject(), vFile, this);
    if (!(tree instanceof StubTree)) return Pair.empty();
    FileViewProvider viewProvider = getViewProvider();
    List<Pair<IStubFileElementType<?>, PsiFile>> roots = StubTreeBuilder.getStubbedRoots(viewProvider);

    try {
      synchronized (myPsiLock) {
        FileElement treeElementOnLock = getTreeElement();
        StubTree dereferencedOnLock = derefStub();
        if (dereferencedOnLock != null || treeElementOnLock != null) {
          return Pair.create(dereferencedOnLock, treeElementOnLock);
        }

        PsiFileStubImpl<?> baseRoot = (PsiFileStubImpl<?>)((StubTree)tree).getRoot();
        if (!baseRoot.rootsAreSet()) {
          LOG.error("Stub roots must be set when stub tree was read or built with StubTreeLoader");
          return Pair.empty();
        }
        PsiFileStub<?>[] stubRoots = baseRoot.getStubRoots();
        if (stubRoots.length != roots.size()) {
          com.intellij.util.Function<PsiFileStub<?>, String> stubToString =
            stub -> "{" + stub.getClass().getSimpleName() + " " + stub.getType().getLanguage() + "}";
          LOG.error("readOrBuilt roots = " + StringUtil.join(stubRoots, stubToString, ", ") + "; " +
                    StubTreeLoader.getFileViewProviderMismatchDiagnostics(viewProvider));
          rebuildStub();
          return Pair.empty();
        }

        StubTree result = null;
        for (int i = 0; i < roots.size(); i++) {
          PsiFileImpl eachPsiRoot = (PsiFileImpl)roots.get(i).second;
          if (eachPsiRoot.derefStub() == null) {
            StubTree stubTree = eachPsiRoot.setStubTree(stubRoots[i]);
            if (eachPsiRoot == this) {
              result = stubTree;
            }
          }
        }

        assert result != null : "Current file not in root list: " + roots + ", vp=" + viewProvider;
        return Pair.create(result, null);
      }
    }
    catch (StubTreeLoader.StubTreeAndIndexUnmatchCoarseException e) {
      throw e.createCompleteException();
    }
  }

  private @NotNull StubTree setStubTree(PsiFileStub<?> root) throws StubTreeLoader.StubTreeAndIndexUnmatchCoarseException {
    //noinspection unchecked
    ((StubBase<PsiFile>)root).setPsi(this);
    StubTree stubTree = new StubTree(root);
    FileElement fileElement = getTreeElement();
    stubTree.setDebugInfo("created in getStubTree(), with AST = " + (fileElement != null));
    updateTrees(myTrees.withStub(stubTree, fileElement));
    return stubTree;
  }

  @ApiStatus.Internal
  public final @Nullable StubTree derefStub() {
    return myTrees.derefStub();
  }

  private void updateTrees(@NotNull FileTrees trees) {
    myTrees = trees;
  }

  protected PsiFileImpl cloneImpl(FileElement treeElementClone) {
    PsiFileImpl clone = (PsiFileImpl)super.clone();
    clone.setTreeElementPointer(treeElementClone); // should not use setTreeElement here because the cloned file still has VirtualFile (SCR17963)
    treeElementClone.setPsi(clone);
    return clone;
  }

  private boolean isKeepTreeElementByHardReference() {
    return !getViewProvider().isEventSystemEnabled();
  }

  private @NotNull Supplier<FileElement> createTreeElementPointer(@NotNull FileElement treeElement) {
    if (isKeepTreeElementByHardReference()) {
      return () -> treeElement;
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

  public final @NotNull FileElement calcTreeElement() {
    FileElement treeElement = getTreeElement();
    return treeElement != null ? treeElement : loadTreeElement();
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return calcTreeElement().getChildrenAsPsiElements((TokenSet)null, ARRAY_FACTORY);
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
    return 0;
  }

  @Override
  public int getTextOffset() {
    return 0;
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
  public PsiReference @NotNull [] getReferences() {
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
  public @NotNull GlobalSearchScope getResolveScope() {
    return ResolveScopeManager.getElementResolveScope(this);
  }

  @Override
  public @NotNull SearchScope getUseScope() {
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
        VirtualFile file = getViewProvider().getVirtualFile().getParent();
        if (file != null && file.isValid() && file.isDirectory()) {
          return file.getPresentableUrl();
        }
        return null;
      }

      @Override
      public Icon getIcon(boolean open) {
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
  public final @NotNull Project getProject() {
    return getManager().getProject();
  }

  @Override
  public @NotNull FileASTNode getNode() {
    return calcTreeElement();
  }

  /**
   * @implNote for non-physical files, this method can still load AST even if it's not yet loaded
   */
  public @Nullable FileASTNode getNodeIfLoaded() {
    return getTreeElement();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return this == another;
  }

  public @NotNull StubTree calcStubTree() {
    StubTree tree = derefStub();
    if (tree != null) {
      return tree;
    }
    FileElement fileElement = calcTreeElement();
    try {
      synchronized (myPsiLock) {
        tree = derefStub();

        if (tree == null) {
          assertReadAccessAllowed();
          IStubFileElementType<?> contentElementType = getElementTypeForStubBuilder();
          if (contentElementType == null) {
            VirtualFile vFile = getVirtualFile();
            String message = "ContentElementType: " + getContentElementType() +
                             "; file: " + this + (vFile.isValid() ? "" : " (" + vFile + " invalid)") +
                             "\n\t" + "Boolean.TRUE.equals(getUserData(BUILDING_STUB)) = " + Boolean.TRUE.equals(getUserData(BUILDING_STUB)) +
                             "\n\t" + "getTreeElement() = " + getTreeElement() +
                             "\n\t" + "vFile instanceof VirtualFileWithId = " + (vFile instanceof VirtualFileWithId) +
                             "\n\t" + "StubUpdatingIndex.canHaveStub(vFile) = " + StubTreeLoader.getInstance().canHaveStub(vFile);
            rebuildStub();
            throw new AssertionError(message);
          }

          StubElement<?> currentStubTree = contentElementType.getBuilder().buildStubTree(this);
          if (currentStubTree == null) {
            throw new AssertionError("Stub tree wasn't built for " + contentElementType + "; file: " + this);
          }

          tree = new StubTree((PsiFileStub<?>)currentStubTree);
          tree.setDebugInfo("created in calcStubTree");
          updateTrees(myTrees.withStub(tree, fileElement));
        }

        return tree;
      }
    }
    catch (StubTreeLoader.StubTreeAndIndexUnmatchCoarseException e) {
      throw e.createCompleteException();
    }
  }

  final void rebuildStub() {
    AppUIExecutor.onWriteThread(ModalityState.nonModal()).later().submit(() -> {
      if (!myManager.isDisposed()) {
        myManager.dropPsiCaches();
      }

      VirtualFile vFile = getVirtualFile();
      if (vFile != null && vFile.isValid()) {
        Document doc = FileDocumentManager.getInstance().getCachedDocument(vFile);
        if (doc != null) {
          FileDocumentManager.getInstance().saveDocument(doc);
        }

        FileContentUtilCore.reparseFiles(vFile);
        StubTreeLoader.getInstance().rebuildStubTree(vFile);
      }
    });
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    putInfo(this, info);
  }

  public static void putInfo(@NotNull PsiFile psiFile, @NotNull Map<? super String, ? super String> info) {
    info.put("fileName", psiFile.getName());
    info.put("fileType", psiFile.getFileType().toString());
  }

  @Override
  public String toString() {
    return myElementType.toString();
  }

  public final void beforeAstChange() {
    checkWritable();
    synchronized (myPsiLock) {
      FileTrees updated = myTrees.switchToStrongRefs();
      if (updated != myTrees) {
        updateTrees(updated);
      }
    }
  }

  private void checkWritable() {
    PsiDocumentManager docManager = PsiDocumentManager.getInstance(getProject());
    if (docManager instanceof PsiDocumentManagerBase &&
        !((PsiDocumentManagerBase)docManager).isCommitInProgress() &&
        !(myViewProvider instanceof FreeThreadedFileViewProvider)) {
      CheckUtil.checkWritable(this);
    }
  }
}
