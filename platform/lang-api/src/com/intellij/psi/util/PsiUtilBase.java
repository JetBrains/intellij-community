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

package com.intellij.psi.util;

import com.intellij.ide.DataManager;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
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

      public <T> T getUserData(@NotNull Key<T> key) {
        return null;
      }

      public <T> void putUserData(@NotNull Key<T> key, T value) {

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
    return findLanguageFromElement(elt);
  }

  @NotNull
  public static Language findLanguageFromElement(final PsiElement elt) {
    if (elt.getFirstChild() == null) { //is leaf
      final PsiElement parent = elt.getParent();
      if (parent != null) {
        return parent.getLanguage();
      }
    }

    return elt.getLanguage();
  }

  @Nullable
  public static PsiFile getTemplateLanguageFile(final PsiElement element) {
    if (element == null) return null;
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
      if (data != null) {
        name = data.getName(element);
      }
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
    if (originalFile == psiFile) return psiElement;
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
  public static Language getLanguageInEditor(@NotNull final Editor editor, @NotNull final Project project) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;

    final SelectionModel selectionModel = editor.getSelectionModel();
    int caretOffset = editor.getCaretModel().getOffset();
    int mostProbablyCorrectLanguageOffset = caretOffset == selectionModel.getSelectionStart() ||
                                            caretOffset == selectionModel.getSelectionEnd()
                                            ? selectionModel.getSelectionStart()
                                            : caretOffset;
    PsiElement elt = getElementAtOffset(file, mostProbablyCorrectLanguageOffset);
    Language lang = findLanguageFromElement(elt);

    if (selectionModel.hasSelection()) {
      final Language rangeLanguage = evaluateLanguageInRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), file);
      if (rangeLanguage == null) return file.getLanguage();

      lang = rangeLanguage;
    }

    return narrowLanguage(lang, file.getLanguage());
  }

  @Nullable
  public static PsiElement getElementAtCaret(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null) return null;
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    return file == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
  }

  public static Language getDialect(@NotNull PsiElement element) {
    return narrowLanguage(element.getLanguage(), element.getContainingFile().getLanguage());
  }

  private static Language narrowLanguage(final Language language, final Language candidate) {
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
    return getPsiFileAtOffset(file, mostProbablyCorrectLanguageOffset);
  }

  public static PsiFile getPsiFileAtOffset(final PsiFile file, final int offset) {
    PsiElement elt = getElementAtOffset(file, offset);

    assert elt.isValid() : elt + "; file: "+file + "; isvalid: "+file.isValid();
    return elt.getContainingFile();
  }

  @Nullable
  public static Language reallyEvaluateLanguageInRange(final int start, final int end, final PsiFile file) {
    Language lang = null;
    int curOffset = start;
    do {
      PsiElement elt = getElementAtOffset(file, curOffset);

      if (!(elt instanceof PsiWhiteSpace)) {
        final Language language = findLanguageFromElement(elt);
        if (lang == null) {
          lang = language;
        }
        else if (lang != language) {
          return null;
        }
      }
      int endOffset = elt.getTextRange().getEndOffset();
      curOffset = endOffset <= curOffset ? curOffset + 1 : endOffset;
    }
    while (curOffset < end);
    return narrowLanguage(lang, file.getLanguage());
  }

  @Nullable
  public static Language evaluateLanguageInRange(final int start, final int end, final PsiFile file) {
    PsiElement elt = getElementAtOffset(file, start);

    TextRange selectionRange = new TextRange(start, end);
    if (!(elt instanceof PsiFile)) {
      elt = elt.getParent();
      TextRange range = elt.getTextRange();
      assert range != null : "Range is null for " + elt + "; " + elt.getClass();
      while(!range.contains(selectionRange) && !(elt instanceof PsiFile)) {
        elt = elt.getParent();
        if (elt == null) break;
        range = elt.getTextRange();
        assert range != null : "Range is null for " + elt + "; " + elt.getClass();
      }
      
      if (elt != null) {
        return elt.getLanguage();
      }
    }

    return reallyEvaluateLanguageInRange(start, end, file);
  }

  @NotNull
  public static PsiElement getElementAtOffset(@NotNull PsiFile file, int offset) {
    PsiElement elt = file.findElementAt(offset);
    if (elt == null && offset > 0) {
      elt = file.findElementAt(offset - 1);
    }
    if (elt == null) {
      return file;
    }
    return elt;
  }

  public static <T extends PsiElement> T copyElementPreservingOriginalLinks(final T element, final Key<PsiElement> originalKey) {
    final PsiElementVisitor originalVisitor = new PsiRecursiveElementWalkingVisitor() {
      public void visitElement(final PsiElement element) {
        element.putCopyableUserData(originalKey, element);
        super.visitElement(element);
      }
    };
    originalVisitor.visitElement(element);

    final PsiElement fileCopy = element.copy();

    final PsiElementVisitor copyVisitor = new PsiRecursiveElementWalkingVisitor() {
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

  public static boolean hasErrorElementChild(PsiElement element) {
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiErrorElement) return true;
    }
    return false;
  }

  @NotNull
  public static ASTNode getRoot(@NotNull ASTNode node) {
    ASTNode child = node;
    do {
      final ASTNode parent = child.getTreeParent();
      if (parent == null) return child;
      child = parent;
    }
    while (true);
  }

  public static Language getNotAnyLanguage(ASTNode node) {
    if (node == null) return Language.ANY;

    final Language lang = node.getElementType().getLanguage();
    return lang == Language.ANY ? getNotAnyLanguage(node.getTreeParent()) : lang;
  }

  /**
   * Tries to find editor for the given element.
   * <p/>
   * There are at least to approaches to achieve the target. Current method is intended to encapsulate both of them::
   * <pre>
   * <ul>
   *   <li>target editor works with a real file that remains at file system;</li>
   *   <li>target editor works with a virtual file;</li>
   * </ul>
   * </pre>
   *
   * @param element   target element
   * @return          editor that works with a given element if the one is found; <code>null</code> otherwise
   */
  @Nullable
  public static Editor findEditor(@NotNull PsiElement element) {
    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    VirtualFile virtualFile = psiFile.getOriginalFile().getVirtualFile();
    if (virtualFile == null) {
      return null;
    }

    VirtualFileSystem fileSystem = virtualFile.getFileSystem();
    if (fileSystem instanceof LocalFileSystem) {
      // Try to find editor for the real file.
      final FileEditor[] editors = FileEditorManager.getInstance(psiFile.getProject()).getEditors(virtualFile);
      for (FileEditor editor : editors) {
        if (editor instanceof TextEditor) {
          return ((TextEditor)editor).getEditor();
        }
      }
    }
    else if (SwingUtilities.isEventDispatchThread()) {
      // We assume that data context from focus-based retrieval should success if performed from EDT.
      AsyncResult<DataContext> asyncResult = DataManager.getInstance().getDataContextFromFocus();
      if (asyncResult.isDone()) {
        Editor editor = PlatformDataKeys.EDITOR.getData(asyncResult.getResult());
        if (editor != null) {
          Document cachedDocument = PsiDocumentManager.getInstance(psiFile.getProject()).getCachedDocument(psiFile);
          // Ensure that target editor is found by checking its document against the one from given PSI element.
          if (cachedDocument == editor.getDocument()) {
            return editor;
          }
        }
      }
    }
    return null;
  }
}
