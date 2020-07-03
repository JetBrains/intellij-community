// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.util;

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

public class PsiUtilBase extends PsiUtilCore implements PsiEditorUtil {
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

  @Nullable
  public static Language getLanguageInEditor(@NotNull final Editor editor, @NotNull final Project project) {
    return getLanguageInEditor(editor.getCaretModel().getCurrentCaret(), project);
  }

  @Nullable
  public static Language getLanguageInEditor(@NotNull Caret caret, @NotNull final Project project) {
    Editor editor = caret.getEditor();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    if (file == null) {
      return null;
    }

    if (file instanceof PsiFileWithOneLanguage) {
      return file.getLanguage();
    }

    int caretOffset = caret.getOffset();
    int mostProbablyCorrectLanguageOffset = caretOffset == caret.getSelectionEnd() ? caret.getSelectionStart() : caretOffset;
    PsiElement elt = getElementAtOffset(file, mostProbablyCorrectLanguageOffset);
    Language lang = findLanguageFromElement(elt);

    if (caret.hasSelection()) {
      lang = evaluateLanguageInRange(caret.getSelectionStart(), caret.getSelectionEnd(), file);
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

  @Nullable
  public static PsiFile getPsiFileInEditor(@NotNull final Editor editor, @NotNull final Project project) {
    return getPsiFileInEditor(editor.getCaretModel().getCurrentCaret(), project);
  }

  @Nullable
  public static PsiFile getPsiFileInEditor(@NotNull Caret caret, @NotNull final Project project) {
    Editor editor = caret.getEditor();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;

    if (file instanceof PsiFileWithOneLanguage) {
      return file;
    }

    PsiUtilCore.ensureValid(file);

    final Language language = getLanguageInEditor(caret, project);

    if (language == file.getLanguage()) return file;

    int caretOffset = caret.getOffset();
    int mostProbablyCorrectLanguageOffset = caretOffset == caret.getSelectionEnd() ? caret.getSelectionStart() : caretOffset;
    return getPsiFileAtOffset(file, mostProbablyCorrectLanguageOffset);
  }

  public static PsiFile getPsiFileAtOffset(@NotNull PsiFile file, final int offset) {
    PsiElement elt = getElementAtOffset(file, offset);
    ensureValid(elt);
    return elt.getContainingFile();
  }

  @Nullable
  public static Language reallyEvaluateLanguageInRange(final int start, final int end, @NotNull PsiFile file) {
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

  @NotNull
  private static Language evaluateLanguageInRange(final int start, final int end, @NotNull PsiFile file) {
    PsiElement elt = getElementAtOffset(file, start);

    TextRange selectionRange = new TextRange(start, end);
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

  /**
   * @deprecated Use {@link PsiEditorUtil#findEditor(PsiElement)}
   */
  @Deprecated
  @Nullable
  @Override
  public Editor findEditorByPsiElement(@NotNull PsiElement element) {
    return findEditor(element);
  }

  /**
   * @deprecated Use {@link PsiEditorUtil#findEditor(PsiElement)}
   */
  @Deprecated
  @Nullable
  public static Editor findEditor(@NotNull PsiElement element) {
    return PsiEditorUtil.findEditor(element);
  }

  public static boolean isSymLink(@NotNull final PsiFileSystemItem element) {
    final VirtualFile virtualFile = element.getVirtualFile();
    return virtualFile != null && virtualFile.is(VFileProperty.SYMLINK);
  }

  @Nullable
  public static VirtualFile asVirtualFile(@Nullable PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      PsiFileSystemItem psiFileSystemItem = (PsiFileSystemItem)element;
      return psiFileSystemItem.isValid() ? psiFileSystemItem.getVirtualFile() : null;
    }
    return null;
  }
}
