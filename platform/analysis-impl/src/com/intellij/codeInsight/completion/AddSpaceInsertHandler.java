// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author zolotov
 */
public class AddSpaceInsertHandler implements InsertHandler<LookupElement> {
  private final static String VALID_COMPLETION_CHARS = "\u0000\n\t\r(,.:=";

  public final static InsertHandler<LookupElement> INSTANCE = CompositeDeclarativeInsertHandler.Companion
    .withUniversalHandler(VALID_COMPLETION_CHARS,
                          new SingleInsertionDeclarativeInsertHandler(" ",
                                                                      DeclarativeInsertHandler2.PopupOptions.DoNotShow.INSTANCE)
    );

  public final static InsertHandler<LookupElement> INSTANCE_WITH_AUTO_POPUP = CompositeDeclarativeInsertHandler.Companion
    .withUniversalHandler(VALID_COMPLETION_CHARS,
                          new SingleInsertionDeclarativeInsertHandler(" ",
                                                                      DeclarativeInsertHandler2.PopupOptions.MemberLookup.INSTANCE)
    );

  private final String myIgnoreOnChars;
  private final boolean myTriggerAutoPopup;

  public AddSpaceInsertHandler(boolean triggerAutoPopup) {
    this("", triggerAutoPopup);
  }

  public AddSpaceInsertHandler(String ignoreOnChars, boolean triggerAutoPopup) {
    myIgnoreOnChars = ignoreOnChars;
    myTriggerAutoPopup = triggerAutoPopup;
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    Editor editor = context.getEditor();
    char completionChar = context.getCompletionChar();
    if (completionChar == ' ' || StringUtil.containsChar(myIgnoreOnChars, completionChar)) return;
    Project project = editor.getProject();
    if (project != null) {
      if (!isCharAtSpace(editor)) {
        EditorModificationUtilEx.insertStringAtCaret(editor, " ");
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      }
      else if (shouldOverwriteExistingSpace(editor)) {
        editor.getCaretModel().moveToOffset(editor.getCaretModel().getOffset() + 1);
      }
      if (myTriggerAutoPopup) {
        AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
      }
    }
  }

  protected boolean shouldOverwriteExistingSpace(Editor editor) {
    return true;
  }

  private static boolean isCharAtSpace(Editor editor) {
    final int startOffset = editor.getCaretModel().getOffset();
    final Document document = editor.getDocument();
    return document.getTextLength() > startOffset && document.getCharsSequence().charAt(startOffset) == ' ';
  }
}
