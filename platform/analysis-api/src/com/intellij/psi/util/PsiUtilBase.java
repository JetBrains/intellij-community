// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.util;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.EditorContextManager;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public final class PsiUtilBase extends PsiUtilCore implements PsiEditorUtil {
  private static final Logger LOG = Logger.getInstance(PsiUtilBase.class);
  public static final Comparator<Language> LANGUAGE_COMPARATOR = Comparator.comparing(Language::getID);

  public static boolean isUnderPsiRoot(@NotNull PsiFile root, @NotNull PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    if (containingFile == root) return true;
    for (PsiFile psiRoot : root.getViewProvider().getAllFiles()) {
      if (containingFile == psiRoot) return true;
    }
    PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(root.getProject()).getInjectionHost(element);
    return host != null && isUnderPsiRoot(root, host);
  }

  public static @Nullable Language getLanguageInEditor(final @NotNull Editor editor, final @NotNull Project project) {
    return getLanguageInEditor(editor.getCaretModel().getCurrentCaret(), project);
  }

  public static @Nullable Language getLanguageInEditor(@NotNull Caret caret, final @NotNull Project project) {
    Editor editor = caret.getEditor();
    assertEditorAndProjectConsistent(project, editor);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    if (file == null) {
      return null;
    }

    if (file instanceof PsiFileWithOneLanguage) {
      return file.getLanguage();
    }

    @NotNull TextRange selection = caret.getSelectionRange();
    int mostProbablyCorrectLanguageOffset = selection.getStartOffset();
    PsiElement elt = getElementAtOffset(file, mostProbablyCorrectLanguageOffset);
    Language lang = findLanguageFromElement(elt);

    if (caret.hasSelection()) {
      lang = evaluateLanguageInRange(selection, file);
    }

    return narrowLanguage(lang, file.getLanguage());
  }

  public static @Nullable PsiElement getElementAtCaret(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null) return null;
    CodeInsightContext context = EditorContextManager.getEditorContext(editor, project);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument(), context);
    return file == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
  }

  public static @Nullable PsiFile getPsiFileInEditor(final @NotNull Editor editor, final @NotNull Project project) {
    return getPsiFileInEditor(editor.getCaretModel().getCurrentCaret(), project);
  }

  public static @Nullable PsiFile getPsiFileInEditor(@NotNull Caret caret, final @NotNull Project project) {
    Editor editor = caret.getEditor();
    assertEditorAndProjectConsistent(project, editor);
    CodeInsightContext context = EditorContextManager.getEditorContext(editor, project);
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument(), context);
    if (psiFile == null) return null;

    PsiUtilCore.ensureValid(psiFile);

    if (psiFile instanceof PsiFileWithOneLanguage) {
      return psiFile;
    }

    final Language language = getLanguageInEditor(caret, project);

    if (language == psiFile.getLanguage()) return psiFile;

    int caretOffset = caret.getOffset();
    int mostProbablyCorrectLanguageOffset = caretOffset == caret.getSelectionEnd() ? caret.getSelectionStart() : caretOffset;
    return getPsiFileAtOffset(psiFile, mostProbablyCorrectLanguageOffset);
  }

  /**
   * assert that {@code editor} belongs to the {@code project}
   */
  public static void assertEditorAndProjectConsistent(@NotNull Project project, @NotNull Editor editor) {
    Project editorProject = editor.getProject();
    if (editorProject != null && editorProject != project) {
      throw new IllegalArgumentException("Inconsistent editor/project combination: the editor belongs to " + editorProject + "; but passed project=" + project);
    }
  }

  public static PsiFile getPsiFileAtOffset(@NotNull PsiFile file, final int offset) {
    if (file instanceof PsiFileWithOneLanguage) {
      return file;
    }

    PsiElement elt = getElementAtOffset(file, offset);
    ensureValid(elt);
    return elt.getContainingFile();
  }

  public static @Nullable Language reallyEvaluateLanguageInRange(final int start, final int end, @NotNull PsiFile file) {
    if (file instanceof PsiBinaryFile || file instanceof PsiFileWithOneLanguage) {
      return file.getLanguage();
    }
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
      TextRange range = elt.getTextRange();
      if (range == null) {
        LOG.error("Null range for element " + elt + " of " + elt.getClass() + " in file " + file + " at offset " + curOffset);
        return file.getLanguage();
      }
      int endOffset = range.getEndOffset();
      curOffset = endOffset <= curOffset ? curOffset + 1 : endOffset;
    }
    while (curOffset < end);
    return narrowLanguage(lang, file.getLanguage());
  }

  private static @NotNull Language evaluateLanguageInRange(TextRange selectionRange, @NotNull PsiFile file) {
    PsiElement elt = getElementAtOffset(file, selectionRange.getStartOffset());

    while (true) {
      if (elt instanceof PsiFile) {
        return elt.getLanguage();
      }
      PsiElement parent = elt.getParent();
      TextRange range = elt.getTextRange();
      if (range == null) {
        LOG.error("Range is null for " + elt + "; " + elt.getClass());
        return file.getLanguage();
      }
      if (range.contains(selectionRange) || parent == null) {
        return elt.getLanguage();
      }
      elt = parent;
    }
  }

  public static @NotNull ASTNode getRoot(@NotNull ASTNode node) {
    ASTNode child = node;
    do {
      final ASTNode parent = child.getTreeParent();
      if (parent == null) return child;
      child = parent;
    }
    while (true);
  }

  /**
   * @deprecated Use {@link PsiEditorUtil#findEditor(PsiElement)}
   */
  @Deprecated
  @Override
  public @Nullable Editor findEditorByPsiElement(@NotNull PsiElement element) {
    return findEditor(element);
  }

  /**
   * @deprecated Use {@link PsiEditorUtil#findEditor(PsiElement)}
   */
  @Deprecated
  public static @Nullable Editor findEditor(@NotNull PsiElement element) {
    return PsiEditorUtil.findEditor(element);
  }

  public static boolean isSymLink(final @NotNull PsiFileSystemItem element) {
    final VirtualFile virtualFile = element.getVirtualFile();
    return virtualFile != null && virtualFile.is(VFileProperty.SYMLINK);
  }

  public static @Nullable VirtualFile asVirtualFile(@Nullable PsiElement element) {
    if (element instanceof PsiFileSystemItem psiFileSystemItem) {
      return psiFileSystemItem.isValid() ? psiFileSystemItem.getVirtualFile() : null;
    }
    return null;
  }
}
