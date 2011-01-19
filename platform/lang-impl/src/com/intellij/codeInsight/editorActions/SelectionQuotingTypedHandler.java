package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;

/**
 * @author AG
 * @author yole
 */
public class SelectionQuotingTypedHandler extends TypedHandlerDelegate {
  private TextRange myReplacedTextRange;

  @Override
  public Result checkAutoPopup(char c, Project project, Editor editor, PsiFile psiFile) {
    myReplacedTextRange = null;
    // TODO[oleg] provide adequate API not to use this hack
    // beforeCharTyped always works with removed selection
    if(CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED &&
      editor.getSelectionModel().hasSelection()
       && isDelimiter(c)) {
      String selectedText = editor.getSelectionModel().getSelectedText();
      if (selectedText.length() <= 1) {
        return super.checkAutoPopup(c, project, editor, psiFile);
      }
      if (isDelimiter(selectedText.charAt(0)) && selectedText.charAt(selectedText.length()-1) == getMatchingDelimiter(selectedText.charAt(0))) {
        selectedText = selectedText.substring(1, selectedText.length()-1);
      }
      char c2 = getMatchingDelimiter(c);
      int caretOffset = editor.getSelectionModel().getSelectionStart();
      String newText = String.valueOf(c) + selectedText + c2;
      EditorModificationUtil.insertStringAtCaret(editor, newText);
      myReplacedTextRange = new TextRange(caretOffset, caretOffset + newText.length());
      return Result.STOP;
    }
    return super.checkAutoPopup(c, project, editor, psiFile);
  }

  private static char getMatchingDelimiter(char c) {
    char c2 = c;
    if (c == '(') c2 = ')';
    if (c == '[') c2 = ']';
    if (c == '{') c2 = '}';
    if (c == '<') c2 = '>';
    return c2;
  }

  private static boolean isDelimiter(char c) {
    return c == '(' || c == '{' || c == '[' || c == '<' || c == '"' || c == '\'';
  }

  public Result beforeCharTyped(final char charTyped, final Project project, final Editor editor, final PsiFile file, final FileType fileType) {
    // TODO[oleg] remove this hack when API changes
    if (myReplacedTextRange != null) {
      editor.getSelectionModel().setSelection(myReplacedTextRange.getStartOffset(), myReplacedTextRange.getEndOffset());
      return Result.STOP;
    }
    return Result.CONTINUE;
  }
}