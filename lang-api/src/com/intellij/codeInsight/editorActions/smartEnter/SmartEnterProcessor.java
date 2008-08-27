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
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 3:31:23 PM
 * To change this template use Options | File Templates.
 */
public abstract class SmartEnterProcessor {
  public abstract boolean process(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile psiFile);

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
    final TextRange textRange = elt.getTextRange();
    return document.createRangeMarker(textRange.getStartOffset(), textRange.getEndOffset());
  }

  @Nullable
  protected PsiElement getStatementAtCaret(Editor editor, PsiFile psiFile) {
    int caret = editor.getCaretModel().getOffset();

    final Document doc = editor.getDocument();
    CharSequence chars = doc.getCharsSequence();
    int offset = CharArrayUtil.shiftBackward(chars, caret - 1, " \t");
    if (doc.getLineNumber(offset) < doc.getLineNumber(caret)) {
      offset = CharArrayUtil.shiftForward(chars, caret, " \t");
    }

    return psiFile.findElementAt(offset);
  }

  protected static boolean isUncommited(@NotNull final Project project) {
    return PsiDocumentManager.getInstance(project).hasUncommitedDocuments();
  }

  protected void commit(@NotNull final Editor editor) {
    final Project project = editor.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

    //some psi operations may block the document, unblock here
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
  }

}
