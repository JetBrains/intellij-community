// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This extension allows to modify the behaviour of 'Complete Current Statement' action in editor, usually bound to Ctrl+Enter
 * (Cmd+Enter on macOS).
 */
public abstract class SmartEnterProcessor {
  /**
   * @return {@code true} if this extension has handled the action processing and no further processing (either defined by another extension
   *         or the default logic) should be performed
   */
  public abstract boolean process(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile);

  public boolean processAfterCompletion(final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
    return process(psiFile.getProject(), editor, psiFile);
  }

  protected void reformat(PsiElement atCaret) throws IncorrectOperationException {
    final TextRange range = atCaret.getTextRange();
    final PsiFile file = atCaret.getContainingFile();
    final PsiFile baseFile = file.getViewProvider().getPsi(file.getViewProvider().getBaseLanguage());
    CodeStyleManager.getInstance(atCaret.getProject()).reformatText(baseFile, range.getStartOffset(), range.getEndOffset());
  }

  protected RangeMarker createRangeMarker(final PsiElement elt) {
    final PsiFile psiFile = elt.getContainingFile();
    final PsiDocumentManager instance = PsiDocumentManager.getInstance(elt.getProject());
    final Document document = instance.getDocument(psiFile);
    return document.createRangeMarker(elt.getTextRange());
  }

  protected @Nullable PsiElement getStatementAtCaret(Editor editor, PsiFile psiFile) {
    int caret = editor.getCaretModel().getOffset();

    final Document doc = editor.getDocument();
    CharSequence chars = doc.getCharsSequence();
    int offset = caret == 0 ? 0 : CharArrayUtil.shiftBackward(chars, caret - 1, " \t");
    if (offset < 0) offset = 0;
    if (doc.getLineNumber(offset) < doc.getLineNumber(caret)) {
      offset = CharArrayUtil.shiftForward(chars, caret, " \t");
    }

    return psiFile.findElementAt(offset);
  }

  protected static boolean isUncommited(final @NotNull Project project) {
    return PsiDocumentManager.getInstance(project).hasUncommitedDocuments();
  }

  public void commit(final @NotNull Editor editor) {
    commitDocument(editor);
  }

  public static void commitDocument(@NotNull Editor editor) {
    final Project project = editor.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

    //some psi operations may block the document, unblock here
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
  }
}
