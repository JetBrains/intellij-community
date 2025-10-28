// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.diagnostic.PluginException;
import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class PsiUtilCore {
  private static final Logger LOG = Logger.getInstance(PsiUtilCore.class);
  public static final PsiElement NULL_PSI_ELEMENT = new NullPsiElement();
  protected static class NullPsiElement implements PsiElement {
    @Override
    public @NotNull Project getProject() {
      throw createException();
    }

    @Override
    public @NotNull Language getLanguage() {
      throw createException();
    }

    @Override
    public PsiManager getManager() {
      throw createException();
    }

    @Override
    public PsiElement @NotNull [] getChildren() {
      throw createException();
    }

    @Override
    public PsiElement getParent() {
      throw createException();
    }

    @Override
    public @Nullable PsiElement getFirstChild() {
      throw createException();
    }

    @Override
    public @Nullable PsiElement getLastChild() {
      throw createException();
    }

    @Override
    public @Nullable PsiElement getNextSibling() {
      throw createException();
    }

    @Override
    public @Nullable PsiElement getPrevSibling() {
      throw createException();
    }

    @Override
    public PsiFile getContainingFile() {
      throw createException();
    }

    @Override
    public TextRange getTextRange() {
      throw createException();
    }

    @Override
    public int getStartOffsetInParent() {
      throw createException();
    }

    @Override
    public int getTextLength() {
      throw createException();
    }

    @Override
    public PsiElement findElementAt(int offset) {
      throw createException();
    }

    @Override
    public @Nullable PsiReference findReferenceAt(int offset) {
      throw createException();
    }

    @Override
    public int getTextOffset() {
      throw createException();
    }

    @Override
    public String getText() {
      throw createException();
    }

    @Override
    public char @NotNull [] textToCharArray() {
      throw createException();
    }

    @Override
    public PsiElement getNavigationElement() {
      throw createException();
    }

    @Override
    public PsiElement getOriginalElement() {
      throw createException();
    }

    @Override
    public boolean textMatches(@NotNull CharSequence text) {
      throw createException();
    }

    @Override
    public boolean textMatches(@NotNull PsiElement element) {
      throw createException();
    }

    @Override
    public boolean textContains(char c) {
      throw createException();
    }

    @Override
    public void accept(@NotNull PsiElementVisitor visitor) {
      throw createException();
    }

    @Override
    public void acceptChildren(@NotNull PsiElementVisitor visitor) {
      throw createException();
    }

    @Override
    public PsiElement copy() {
      throw createException();
    }

    @Override
    public PsiElement add(@NotNull PsiElement element) {
      throw createException();
    }

    @Override
    public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) {
      throw createException();
    }

    @Override
    public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) {
      throw createException();
    }

    @Override
    public void checkAdd(@NotNull PsiElement element) {
      throw createException();
    }

    @Override
    public PsiElement addRange(PsiElement first, PsiElement last) {
      throw createException();
    }

    @Override
    public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor) {
      throw createException();
    }

    @Override
    public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) {
      throw createException();
    }

    @Override
    public void delete() {
      throw createException();
    }

    @Override
    public void checkDelete() {
      throw createException();
    }

    @Override
    public void deleteChildRange(PsiElement first, PsiElement last) {
      throw createException();
    }

    @Override
    public PsiElement replace(@NotNull PsiElement newElement) {
      throw createException();
    }

    @Override
    public boolean isValid() {
      throw createException();
    }

    @Override
    public boolean isWritable() {
      throw createException();
    }

    PsiInvalidElementAccessException createException() {
      return new PsiInvalidElementAccessException(this, toString(), null);
    }

    @Override
    public @Nullable PsiReference getReference() {
      throw createException();
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
      throw createException();
    }

    @Override
    public <T> T getCopyableUserData(@NotNull Key<T> key) {
      throw createException();
    }

    @Override
    public <T> void putCopyableUserData(@NotNull Key<T> key, T value) {
      throw createException();
    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                       @NotNull ResolveState state,
                                       PsiElement lastParent,
                                       @NotNull PsiElement place) {
      throw createException();
    }

    @Override
    public PsiElement getContext() {
      throw createException();
    }

    @Override
    public boolean isPhysical() {
      throw createException();
    }

    @Override
    public @NotNull GlobalSearchScope getResolveScope() {
      throw createException();
    }

    @Override
    public @NotNull SearchScope getUseScope() {
      throw createException();
    }

    @Override
    public ASTNode getNode() {
      throw createException();
    }

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      throw createException();
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, T value) {
      throw createException();
    }

    @Override
    public Icon getIcon(int flags) {
      throw createException();
    }

    @Override
    public boolean isEquivalentTo(PsiElement another) {
      return this == another;
    }

    @Override
    public String toString() {
      return "NULL_PSI_ELEMENT";
    }
  }

  public static PsiElement @NotNull [] toPsiElementArray(@NotNull Collection<? extends PsiElement> collection) {
    return collection.isEmpty() ? PsiElement.EMPTY_ARRAY : collection.toArray(PsiElement.EMPTY_ARRAY);
  }

  public static Language getNotAnyLanguage(ASTNode node) {
    if (node == null) return Language.ANY;

    Language lang = node.getElementType().getLanguage();
    return lang == Language.ANY ? getNotAnyLanguage(node.getTreeParent()) : lang;
  }

  public static @Nullable VirtualFile getVirtualFile(@Nullable PsiElement element) {
    // optimisation: call isValid() on file only to reduce walks up and down
    if (element == null) {
      return null;
    }
    if (element instanceof PsiFileSystemItem) {
      return element.isValid() ? ((PsiFileSystemItem)element).getVirtualFile() : null;
    }
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == null || !containingFile.isValid()) {
      return null;
    }

    VirtualFile file = containingFile.getVirtualFile();
    if (file == null) {
      PsiFile originalFile = containingFile.getOriginalFile();
      if (originalFile != containingFile && originalFile.isValid()) {
        file = originalFile.getVirtualFile();
      }
    }
    return file;
  }

  public static int compareElementsByPosition(@Nullable PsiElement element1, @Nullable PsiElement element2) {
    if (element1 == null && element2 == null) return 0;
    if (element1 == null) return -1;
    if (element2 == null) return 1;
    if (element1.equals(element2)) return 0;

    PsiFile psiFile1 = element1.getContainingFile();
    PsiFile psiFile2 = element2.getContainingFile();
    if (psiFile1 == null && psiFile2 == null) return 0;
    if (psiFile1 == null) return -1;
    if (psiFile2 == null) return 1;

    if (!psiFile1.equals(psiFile2)) {
      String name1 = psiFile1.getName();
      String name2 = psiFile2.getName();
      return name1.compareToIgnoreCase(name2);
    }
    if (element1 instanceof StubBasedPsiElement && element2 instanceof StubBasedPsiElement) {
      StubElement<?> stub1 = ((StubBasedPsiElement<?>)element1).getStub();
      StubElement<?> stub2 = ((StubBasedPsiElement<?>)element2).getStub();
      if (stub1 != null && stub2 != null) {
        return compareStubPositions(stub1, stub2);
      }
    }
    TextRange textRange1 = element1.getTextRange();
    TextRange textRange2 = element2.getTextRange();
    if (textRange1 == null && textRange2 == null) return 0;
    if (textRange1 == null) return -1;
    if (textRange2 == null) return 1;
    return Segment.BY_START_OFFSET_THEN_END_OFFSET.compare(textRange1, textRange2);
  }

  private static int compareStubPositions(StubElement<?> stub1, StubElement<?> stub2) {
    int depth1 = getStubDepth(stub1);
    int depth2 = getStubDepth(stub2);
    int diff = Integer.compare(depth1, depth2);
    while (depth1 > depth2) {
      stub1 = stub1.getParentStub();
      depth1--;
    }
    while (depth2 > depth1) {
      stub2 = stub2.getParentStub();
      depth2--;
    }
    int cmp = compareBalancedStubs(stub1, stub2);
    return cmp == 0 ? diff : cmp;
  }

  private static int getStubDepth(StubElement<?> stub) {
    int depth = 0;
    while (stub != null) {
      stub = stub.getParentStub();
      depth++;
    }
    return depth;
  }

  private static int compareBalancedStubs(StubElement<?> stub1, StubElement<?> stub2) {
    if (stub1 == stub2) return 0;
    StubElement<?> parent1 = stub1.getParentStub();
    StubElement<?> parent2 = stub2.getParentStub();
    int parentCmp = compareBalancedStubs(parent1, parent2);
    if (parentCmp != 0) return parentCmp;
    return Integer.compare(parent1.getChildrenStubs().indexOf(stub1), parent2.getChildrenStubs().indexOf(stub2));
  }

  /**
   * Returns whether the element has a child that is an instance of {@link PsiErrorElement} or not.
   */
  public static boolean hasErrorElementChild(@NotNull PsiElement element) {
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiErrorElement) return true;
    }
    return false;
  }

  public static @NotNull PsiElement getElementAtOffset(@NotNull PsiFile psiFile, int offset) {
    PsiElement elt = psiFile.findElementAt(offset);
    if (elt == null && offset > 0) {
      elt = psiFile.findElementAt(offset - 1);
    }
    return elt == null ? psiFile : elt;
  }

  public static PsiFile getTemplateLanguageFile(@Nullable PsiElement element) {
    if (element == null) return null;
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;

    return TemplateLanguageUtil.getBaseFile(containingFile);
  }

  public static PsiFile @NotNull [] toPsiFileArray(@NotNull Collection<? extends PsiFile> collection) {
    if (collection.isEmpty()) return PsiFile.EMPTY_ARRAY;
    return collection.toArray(PsiFile.EMPTY_ARRAY);
  }

  public static @NotNull @Unmodifiable List<PsiFile> toPsiFiles(@NotNull PsiManager psiManager, @NotNull Collection<? extends VirtualFile> virtualFiles) {
    return ContainerUtil.mapNotNull(virtualFiles, psiManager::findFile);
  }

  /**
   * @return name for element using element structure info
   */
  public static String getName(PsiElement element) {
    String name = null;
    if (element instanceof PsiMetaOwner) {
      PsiMetaData data = ((PsiMetaOwner) element).getMetaData();
      if (data != null) {
        name = data.getName(element);
      }
    }
    if (name == null && element instanceof PsiNamedElement) {
      name = ((PsiNamedElement) element).getName();
    }
    return name;
  }

  public static @NotNull String getQualifiedNameAfterRename(String qName, @NotNull String newName) {
    if (qName == null) return newName;
    int index = qName.lastIndexOf('.');
    return index < 0 ? newName : qName.substring(0, index + 1) + newName;
  }

  public static @NotNull Language getDialect(@NotNull PsiElement element) {
    return narrowLanguage(element.getLanguage(), element.getContainingFile().getLanguage());
  }

  protected static @NotNull Language narrowLanguage(@NotNull Language language, @NotNull Language candidate) {
    return candidate.isKindOf(language) ? candidate : language;
  }

  private static final boolean ourSleepDuringValidityCheck = Registry.is("psi.sleep.in.validity.check");

  /**
   * Checks if the element is valid. If not, throws {@link PsiInvalidElementAccessException} with
   * a meaningful message that points to the reasons why the element is not valid and may contain the stack trace
   * when it was invalidated.
   */
  public static void ensureValid(@NotNull PsiElement element) {
    if (!element.isValid()) {
      if (ourSleepDuringValidityCheck) {
        TimeoutUtil.sleep(1); // to see if processing in another thread suddenly makes the element valid again (which is a bug)
        if (element.isValid()) {
          LOG.error("PSI resurrected: " + element + " of " + element.getClass());
          return;
        }
      }
      PsiInvalidElementAccessException exception = new PsiInvalidElementAccessException(element);
      throw PluginException.createByClass(exception, element.getClass());
    }
  }

  @RequiresBackgroundThread(generateAssertion = false)
  public static @Nullable PsiFileSystemItem findFileSystemItem(@Nullable Project project, @Nullable VirtualFile file) {
    if (project == null || file == null) return null;
    if (project.isDisposed() || !file.isValid()) return null;
    PsiManager psiManager = PsiManager.getInstance(project);
    return file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file);
  }

  /**
   * Tries to find PSI file for a virtual file and throws assertion error with debug info if it is null.
   */
  public static @NotNull PsiFile getPsiFile(@NotNull Project project, @NotNull VirtualFile file) {
    PsiManager psiManager = PsiManager.getInstance(project);
    PsiFile psi = psiManager.findFile(file);
    if (psi == null) {
      logFileIsNotFound(file, psiManager, project);
      throw new AssertionError();
    }

    return psi;
  }

  private static void logFileIsNotFound(@NotNull VirtualFile file,
                                        @NotNull PsiManager psiManager,
                                        @NotNull Project project) {
    FileType fileType = file.getFileType();
    FileViewProvider viewProvider = psiManager.findViewProvider(file);
    Document document = FileDocumentManager.getInstance().getDocument(file);
    boolean ignored = !(file instanceof LightVirtualFile) && FileTypeRegistry.getInstance().isFileIgnored(file);
    VirtualFile vDir = file.getParent();
    PsiDirectory psiDir = vDir == null ? null : PsiManager.getInstance(project).findDirectory(vDir);
    FileIndexFacade indexFacade = FileIndexFacade.getInstance(project);
    @NonNls StringBuilder sb = new StringBuilder();
    sb.append("valid=").append(file.isValid()).
      append(" isDirectory=").append(file.isDirectory()).
      append(" hasDocument=").append(document != null).
      append(" length=").append(file.getLength());
    sb.append("\nproject=").append(project.getName()).
      append(" default=").append(project.isDefault()).
      append(" open=").append(project.isOpen());
    sb.append("\nfileType=").append(fileType.getName()).append("/").append(fileType.getClass().getName());
    sb.append("\nisIgnored=").append(ignored);
    sb.append(" underIgnored=").append(indexFacade.isUnderIgnored(file));
    sb.append(" inLibrary=").append(indexFacade.isInLibrary(file));
    sb.append(" parentDir=").append(vDir == null ? "no-vfs" : vDir.isDirectory() ? "has-vfs-dir" : "has-vfs-file").
      append("/").append(psiDir == null ? "no-psi" : "has-psi");
    sb.append("\nviewProvider=").append(viewProvider == null ? "null" : viewProvider.getClass().getName());
    if (viewProvider != null) {
      List<PsiFile> files = viewProvider.getAllFiles();
      sb.append(" language=").append(viewProvider.getBaseLanguage().getID());
      sb.append(" physical=").append(viewProvider.isPhysical());
      sb.append(" rootCount=").append(files.size());
      for (PsiFile o : files) {
        sb.append("\n  root=").append(o.getLanguage().getID()).append("/").append(o.getClass().getName());
      }
    }
    LOG.error("no PSI for file '" + file.getName() + "'", new Attachment(file.getPresentableUrl(), sb.toString()));
  }


  /**
   * @deprecated use CompletionUtil#getOriginalElement where appropriate instead
   */
  @Deprecated
  public static @Nullable <T extends PsiElement> T getOriginalElement(@NotNull T psiElement, @NotNull Class<? extends T> elementClass) {
    PsiFile psiFile = psiElement.getContainingFile();
    PsiFile originalFile = psiFile.getOriginalFile();
    if (originalFile == psiFile) return psiElement;
    TextRange range = psiElement.getTextRange();
    PsiElement element = originalFile.findElementAt(range.getStartOffset());
    int maxLength = range.getLength();
    T parent = PsiTreeUtil.getParentOfType(element, elementClass, false);
    T next = parent ;
    while (next != null && next.getTextLength() <= maxLength) {
      parent = next;
      next = PsiTreeUtil.getParentOfType(next, elementClass, true);
    }
    return parent;
  }

  public static @NotNull Language findLanguageFromElement(@NotNull PsiElement elt) {
    if (!(elt instanceof PsiFile) && elt.getFirstChild() == null) { //is leaf
      PsiElement parent = elt.getParent();
      if (parent != null) {
        return parent.getLanguage();
      }
    }

    return elt.getLanguage();
  }

  public static @NotNull Language getLanguageAtOffset (@NotNull PsiFile psiFile, int offset) {
    PsiElement elt = psiFile.findElementAt(offset);
    if (elt == null) return psiFile.getLanguage();
    if (elt instanceof PsiWhiteSpace) {
      TextRange textRange = elt.getTextRange();
      if (!textRange.contains(offset)) {
        LOG.error("PSI corrupted: in file "+psiFile+" ("+psiFile.getViewProvider().getVirtualFile()+") offset="+offset+" returned element "+elt+" with text range "+textRange);
      }
      int decremented = textRange.getStartOffset() - 1;
      if (decremented >= 0) {
        return getLanguageAtOffset(psiFile, decremented);
      }
    }
    return findLanguageFromElement(elt);
  }

  public static @NotNull Project getProjectInReadAction(@NotNull PsiElement element) {
    return ReadAction.compute(() -> element.getProject());
  }

  @Contract("null -> null;!null -> !null")
  public static IElementType getElementType(@Nullable ASTNode node) {
    return node == null ? null : node.getElementType();
  }

  @Contract("null -> null")
  public static IElementType getElementType(@Nullable PsiElement element) {
    return element == null ? null :
           element instanceof StubBasedPsiElement ? ((StubBasedPsiElement<?>)element).getIElementType() :
           element instanceof PsiFile ? ((PsiFile)element).getFileElementType() :
           getElementType(element.getNode());
  }

  public static final PsiFile NULL_PSI_FILE = new NullPsiFile();
  private static class NullPsiFile extends NullPsiElement implements PsiFile {
    @Override
    public FileASTNode getNode() {
      throw createException();
    }

    @Override
    public PsiDirectory getParent() {
      throw createException();
    }

    @Override
    public VirtualFile getVirtualFile() {
      throw createException();
    }

    @Override
    public PsiDirectory getContainingDirectory() {
      throw createException();
    }

    @Override
    public long getModificationStamp() {
      throw createException();
    }

    @Override
    public @NotNull PsiFile getOriginalFile() {
      throw createException();
    }

    @Override
    public @NotNull FileType getFileType() {
      throw createException();
    }

    @Override
    public PsiFile @NotNull [] getPsiRoots() {
      throw createException();
    }

    @Override
    public @NotNull FileViewProvider getViewProvider() {
      throw createException();
    }

    @Override
    public void subtreeChanged() {
      throw createException();
    }

    @Override
    public boolean isDirectory() {
      throw createException();
    }

    @Override
    public @NotNull String getName() {
      throw createException();
    }

    @Override
    public boolean processChildren(@NotNull PsiElementProcessor<? super PsiFileSystemItem> processor) {
      throw createException();
    }

    @Override
    public @Nullable ItemPresentation getPresentation() {
      throw createException();
    }

    @Override
    public void navigate(boolean requestFocus) {
      throw createException();
    }

    @Override
    public boolean canNavigate() {
      throw createException();
    }

    @Override
    public boolean canNavigateToSource() {
      throw createException();
    }

    @Override
    public void checkSetName(String name) throws IncorrectOperationException {
      throw createException();
    }

    @Override
    public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
      throw createException();
    }

    @Override
    public String toString() {
      return "NULL_PSI_FILE";
    }
  }
}
