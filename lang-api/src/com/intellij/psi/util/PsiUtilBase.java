package com.intellij.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PsiUtilBase {
  public static final PsiElement NULL_PSI_ELEMENT = new PsiElement() {
      @NotNull
      public Project getProject() {
        throw new PsiInvalidElementAccessException(this);
      }

      @NotNull
      public Language getLanguage() {
        throw new IllegalAccessError();
      }

      public PsiManager getManager() {
        return null;
      }

      @NotNull
      public PsiElement[] getChildren() {
        return new PsiElement[0];
      }

      public PsiElement getParent() {
        return null;
      }

      @Nullable
      public PsiElement getFirstChild() {
        return null;
      }

      @Nullable
      public PsiElement getLastChild() {
        return null;
      }

      @Nullable
      public PsiElement getNextSibling() {
        return null;
      }

      @Nullable
      public PsiElement getPrevSibling() {
        return null;
      }

      public PsiFile getContainingFile() {
        throw new PsiInvalidElementAccessException(this);
      }

      public TextRange getTextRange() {
        return null;
      }

      public int getStartOffsetInParent() {
        return 0;
      }

      public int getTextLength() {
        return 0;
      }

      public PsiElement findElementAt(int offset) {
        return null;
      }

      @Nullable
      public PsiReference findReferenceAt(int offset) {
        return null;
      }

      public int getTextOffset() {
        return 0;
      }

      public String getText() {
        return null;
      }

      @NotNull
      public char[] textToCharArray() {
        return new char[0];
      }

      public PsiElement getNavigationElement() {
        return null;
      }

      public PsiElement getOriginalElement() {
        return null;
      }

      public boolean textMatches(@NotNull CharSequence text) {
        return false;
      }

      public boolean textMatches(@NotNull PsiElement element) {
        return false;
      }

      public boolean textContains(char c) {
        return false;
      }

      public void accept(@NotNull PsiElementVisitor visitor) {

      }

      public void acceptChildren(@NotNull PsiElementVisitor visitor) {

      }

      public PsiElement copy() {
        return null;
      }

      public PsiElement add(@NotNull PsiElement element) {
        return null;
      }

      public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) {
        return null;
      }

      public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) {
        return null;
      }

      public void checkAdd(@NotNull PsiElement element) {

      }

      public PsiElement addRange(PsiElement first, PsiElement last) {
        return null;
      }

      public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor) {
        return null;
      }

      public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) {
        return null;
      }

      public void delete() {

      }

      public void checkDelete() {

      }

      public void deleteChildRange(PsiElement first, PsiElement last) {

      }

      public PsiElement replace(@NotNull PsiElement newElement) {
        return null;
      }

      public boolean isValid() {
        return false;
      }

      public boolean isWritable() {
        return false;
      }

      @Nullable
      public PsiReference getReference() {
        return null;
      }

      @NotNull
      public PsiReference[] getReferences() {
        return new PsiReference[0];
      }

      public <T> T getCopyableUserData(Key<T> key) {
        return null;
      }

      public <T> void putCopyableUserData(Key<T> key, T value) {

      }

      public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
        return false;
      }

      public PsiElement getContext() {
        return null;
      }

      public boolean isPhysical() {
        return false;
      }

      @NotNull
      public GlobalSearchScope getResolveScope() {
        return GlobalSearchScope.EMPTY_SCOPE;
      }

      @NotNull
      public SearchScope getUseScope() {
        return GlobalSearchScope.EMPTY_SCOPE;
      }

      public ASTNode getNode() {
        return null;
      }

      public <T> T getUserData(Key<T> key) {
        return null;
      }

      public <T> void putUserData(Key<T> key, T value) {

      }

      public Icon getIcon(int flags) {
        return null;
      }

      public boolean isEquivalentTo(final PsiElement another) {
        return this == another;
      }
  };

  public static final PsiParser NULL_PARSER = new PsiParser() {
    @NotNull
    public ASTNode parse(IElementType root, PsiBuilder builder) {
      throw new IllegalAccessError();
    }
  };

  public static int getRootIndex(PsiElement root) {
    ASTNode node = root.getNode();
    while(node != null && node.getTreeParent() != null) {
      node = node.getTreeParent();
    }
    if(node != null) root = node.getPsi();
    final PsiFile containingFile = root.getContainingFile();
    final PsiFile[] psiRoots = containingFile.getPsiRoots();
    for (int i = 0; i < psiRoots.length; i++) {
      if(root == psiRoots[i]) return i;
    }
    throw new RuntimeException("invalid element");
  }

  @Nullable
  public static VirtualFile getVirtualFile(@Nullable PsiElement element) {
    if (element == null || !element.isValid()) {
      return null;
    }

    if (element instanceof PsiFileSystemItem) {
      return ((PsiFileSystemItem)element).getVirtualFile();
    }

    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) {
      return null;
    }

    return containingFile.getVirtualFile();
  }

  public static int compareElementsByPosition(final PsiElement element1, final PsiElement element2) {
    if (element1 != null && element2 != null) {
      final PsiFile psiFile1 = element1.getContainingFile();
      final PsiFile psiFile2 = element2.getContainingFile();
      if (Comparing.equal(psiFile1, psiFile2)){
        final TextRange textRange1 = element1.getTextRange();
        final TextRange textRange2 = element2.getTextRange();
        if (textRange1 != null && textRange2 != null) {
          return textRange1.getStartOffset() - textRange2.getStartOffset();
        }
      } else if (psiFile1 != null && psiFile2 != null){
        final String name1 = psiFile1.getName();
        final String name2 = psiFile2.getName();
        return name1.compareToIgnoreCase(name2);
      }
    }
    return 0;
  }

  @NotNull
  public static Language getLanguageAtOffset (@NotNull PsiFile file, int offset) {
    final PsiElement elt = file.findElementAt(offset);
    if (elt == null) return file.getLanguage();
    if (elt instanceof PsiWhiteSpace) {
      final int decremented = elt.getTextRange().getStartOffset() - 1;
      if (decremented >= 0) {
        return getLanguageAtOffset(file, decremented);
      }
    }
    return findLanguageFromElement(elt, file);
  }

  @NotNull
  public static Language findLanguageFromElement(final PsiElement elt, final PsiFile file) {
    return file.getViewProvider().getRootLanguage(elt);
  }

  public static PsiFile getTemplateLanguageFile(final PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) return null;

    final FileViewProvider viewProvider = containingFile.getViewProvider();
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  /** @return name for element using element structure info
   */
  @Nullable
  public static String getName(PsiElement element) {
    String name = null;
    if (element instanceof PsiMetaOwner) {
      final PsiMetaData data = ((PsiMetaOwner) element).getMetaData();
      if (data != null)
        name = data.getName(element);
    }
    if (name == null && element instanceof PsiNamedElement) {
      name = ((PsiNamedElement) element).getName();
    }
    return name;
  }

  public static boolean isUnderPsiRoot(PsiFile root, PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == root) return true;
    for (PsiFile psiRoot : root.getPsiRoots()) {
      if (containingFile == psiRoot) return true;
    }
    PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(root.getProject()).getInjectionHost(element);
    return host != null && isUnderPsiRoot(root, host);
  }

  @Nullable
  public static <T extends PsiElement> T getOriginalElement(@NotNull T psiElement, final Class<? extends T> elementClass) {
    final PsiFile psiFile = psiElement.getContainingFile();
    final PsiFile originalFile = psiFile.getOriginalFile();
    if (originalFile == null) return psiElement;
    final TextRange range = psiElement.getTextRange();
    final PsiElement element = originalFile.findElementAt(range.getStartOffset());
    final int maxLength = range.getLength();
    T parent = PsiTreeUtil.getParentOfType(element, elementClass, false);
    for (T next = parent ;
         next != null && next.getTextLength() <= maxLength;
         parent = next, next = PsiTreeUtil.getParentOfType(next, elementClass, true)) {
    }
    return parent;
  }

  @Nullable
  public static Language getLanguageInEditor(final Editor editor, final Project project) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;

    final SelectionModel selectionModel = editor.getSelectionModel();
    int caretOffset = editor.getCaretModel().getOffset();
    int mostProbablyCorrectLanguageOffset = caretOffset == selectionModel.getSelectionStart() ||
                                            caretOffset == selectionModel.getSelectionEnd()
                                            ? selectionModel.getSelectionStart()
                                            : caretOffset;
    PsiElement elt = getElementAtOffset(file, mostProbablyCorrectLanguageOffset);
    Language lang = elt != null ? findLanguageFromElement(elt, file): null;
    if (lang == null) return null;

    if (selectionModel.hasSelection()) {
      lang = evaluateLanguageInRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), file, lang);
    }

    return narrowLanguage(lang, file.getLanguage());
  }

  public static Language getDialect(@NotNull PsiElement element) {
    return narrowLanguage(element.getLanguage(), element.getContainingFile().getLanguage());
  }

  public static Language narrowLanguage(final Language language, final Language candidate) {
    if (candidate.isKindOf(language)) return candidate;
    return language;
  }

  @Nullable
  public static PsiFile getPsiFileInEditor(final Editor editor, final Project project) {
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;

    final Language language = getLanguageInEditor(editor, project);
    if (language == null) return file;

    if (language == file.getLanguage()) return file;

    final SelectionModel selectionModel = editor.getSelectionModel();
    int caretOffset = editor.getCaretModel().getOffset();
    int mostProbablyCorrectLanguageOffset = caretOffset == selectionModel.getSelectionStart() ||
                                            caretOffset == selectionModel.getSelectionEnd()
                                            ? selectionModel.getSelectionStart()
                                            : caretOffset;
    PsiElement elt = getElementAtOffset(file, mostProbablyCorrectLanguageOffset);
    if (elt == null) return file;

    return elt.getContainingFile();
  }

  public static Language evaluateLanguageInRange(final int start, final int end, final PsiFile file, Language lang) {
    PsiElement elt;
    int curOffset = start;
    do {
      elt = getElementAtOffset(file,curOffset);
      if (elt == null) break;
      if (!(elt instanceof PsiWhiteSpace)) {
        if (!Comparing.equal(lang, findLanguageFromElement(elt,file))) {
          lang = file.getLanguage();
          break;
        }
      }
      int endOffset = elt.getTextRange().getEndOffset();
      if (endOffset == curOffset) {
        endOffset++;
      }
      curOffset = endOffset;
    } while(curOffset < end);
    return narrowLanguage(lang, file.getLanguage());
  }

  public static @Nullable PsiElement getElementAtOffset(@NotNull PsiFile file, int offset) {
    PsiElement elt = file.findElementAt(offset);
    if (elt == null && offset > 0) {
      elt = file.findElementAt(offset - 1);
    }

    return elt;
  }

  public static <T extends PsiElement> T copyElementPreservingOriginalLinks(final T element, final Key<PsiElement> originalKey) {
    final PsiElementVisitor originalVisitor = new PsiRecursiveElementVisitor() {
      public void visitElement(final PsiElement element) {
        element.putCopyableUserData(originalKey, element);
        super.visitElement(element);
      }
    };
    originalVisitor.visitElement(element);


    final PsiElement fileCopy = element.copy();

    final PsiElementVisitor copyVisitor = new PsiRecursiveElementVisitor() {

      public void visitElement(final PsiElement element) {
        final PsiElement originalElement = element.getCopyableUserData(originalKey);
        if (originalElement != null) {
          originalElement.putCopyableUserData(originalKey, null);
          element.putCopyableUserData(originalKey, null);
          element.putUserData(originalKey, originalElement);
        }
        super.visitElement(element);
      }

    };
    copyVisitor.visitElement(fileCopy);
    return (T) fileCopy;
  }
}
