package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;

/**
 * @author zolotov
 */
public class AddSpaceInsertHandler implements InsertHandler<LookupElement> {
  public final static InsertHandler<LookupElement> INSTANCE = new AddSpaceInsertHandler(false);
  public final static InsertHandler<LookupElement> INSTANCE_WITH_AUTO_POPUP = new AddSpaceInsertHandler(true);

  private final String myIgnoreOnChars;
  private final boolean myTriggerAutoPopup;

  public AddSpaceInsertHandler(boolean triggerAutoPopup) {
    this("", triggerAutoPopup);
  }

  public AddSpaceInsertHandler(String ignoreOnChars, boolean triggerAutoPopup) {
    myIgnoreOnChars = ignoreOnChars;
    myTriggerAutoPopup = triggerAutoPopup;
  }

  public void handleInsert(InsertionContext context, LookupElement item) {
    Editor editor = context.getEditor();
    char completionChar = context.getCompletionChar();
    if (completionChar == ' ' || StringUtil.containsChar(myIgnoreOnChars, completionChar)) return;
    Project project = editor.getProject();
    if (project != null) {
      if (!isCharAtSpace(editor)) {
        EditorModificationUtil.insertStringAtCaret(editor, " ");
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      }
      else if (shouldMoveCaret(editor)) {
        editor.getCaretModel().moveToOffset(editor.getCaretModel().getOffset() + 1);
      }
      if (myTriggerAutoPopup) {
        AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
      }
    }
  }

  private static boolean shouldMoveCaret(Editor editor) {
    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    TextRange range = state == null ? null : state.getCurrentVariableRange();
    int offset = editor.getCaretModel().getOffset();
    return range == null || offset != range.getEndOffset();
  }

  private static boolean isCharAtSpace(Editor editor) {
    final int startOffset = editor.getCaretModel().getOffset();
    final Document document = editor.getDocument();
    return document.getTextLength() > startOffset && document.getCharsSequence().charAt(startOffset) == ' ';
  }
}
