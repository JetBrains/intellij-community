package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;

/**
 * @author zolotov
 */
public class AddSpaceInsertHandler implements InsertHandler<LookupElement> {
  public final static InsertHandler<LookupElement> INSTANCE = new AddSpaceInsertHandler(false);
  public final static InsertHandler<LookupElement> INSTANCE_WITH_AUTO_POPUP = new AddSpaceInsertHandler(true);

  private final boolean myTriggerAutoPopup;

  public AddSpaceInsertHandler(boolean triggerAutoPopup) {
    myTriggerAutoPopup = triggerAutoPopup;
  }

  public void handleInsert(InsertionContext context, LookupElement item) {
    Editor editor = context.getEditor();
    if (context.getCompletionChar() == ' ') return;
    Project project = editor.getProject();
    if (project != null) {
      if (!isCharAtSpace(editor)) {
        EditorModificationUtil.insertStringAtCaret(editor, " ");
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      }
      else {
        editor.getCaretModel().moveToOffset(editor.getCaretModel().getOffset() + 1);
      }
      if (myTriggerAutoPopup) {
        AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
      }
    }
  }

  private static boolean isCharAtSpace(Editor editor) {
    final int startOffset = editor.getCaretModel().getOffset();
    final Document document = editor.getDocument();
    return document.getTextLength() > startOffset && document.getCharsSequence().charAt(startOffset) == ' ';
  }
}
