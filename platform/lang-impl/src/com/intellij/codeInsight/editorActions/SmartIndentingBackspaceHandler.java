/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public class SmartIndentingBackspaceHandler extends AbstractIndentingBackspaceHandler {
  private static final Logger LOG = Logger.getInstance(SmartIndentingBackspaceHandler.class);

  private String myReplacement;
  private int myStartOffset;

  public SmartIndentingBackspaceHandler() {
    super(SmartBackspaceMode.AUTOINDENT);
  }

  @Override
  protected void doBeforeCharDeleted(char c, PsiFile file, Editor editor) {
    Project project = file.getProject();
    Document document = editor.getDocument();
    CharSequence charSequence = document.getImmutableCharSequence();
    CaretModel caretModel = editor.getCaretModel();
    int caretOffset = caretModel.getOffset();
    LogicalPosition pos = caretModel.getLogicalPosition();
    int lineStartOffset = document.getLineStartOffset(pos.line);
    int beforeWhitespaceOffset = CharArrayUtil.shiftBackward(charSequence, caretOffset - 1, " \t") + 1;
    if (beforeWhitespaceOffset != lineStartOffset) {
      myReplacement = null;
      return;
    }
    PsiDocumentManager.getInstance(project).commitDocument(document);
    CodeStyleFacade codeStyleFacade = CodeStyleFacade.getInstance(project);
    myReplacement = codeStyleFacade.getLineIndent(editor, file.getLanguage(), lineStartOffset, true);
    if (myReplacement == null) {
      return;
    }
    int tabSize = codeStyleFacade.getTabSize(file.getFileType());
    int targetColumn = getWidth(myReplacement, tabSize);
    int endOffset = CharArrayUtil.shiftForward(charSequence, caretOffset, " \t");
    LogicalPosition logicalPosition = caretOffset < endOffset ? editor.offsetToLogicalPosition(endOffset) : pos;
    int currentColumn = logicalPosition.column;
    if (currentColumn > targetColumn) {
      myStartOffset = lineStartOffset;
    }
    else if (logicalPosition.line == 0) {
      myStartOffset = 0;
      myReplacement = "";
    }
    else {
      int prevLineEndOffset = document.getLineEndOffset(logicalPosition.line - 1);
      myStartOffset = CharArrayUtil.shiftBackward(charSequence, prevLineEndOffset - 1, " \t") + 1;
      if (myStartOffset != document.getLineStartOffset(logicalPosition.line - 1)) {
        int spacing = CodeStyleManager.getInstance(project).getSpacing(file, endOffset);
        myReplacement = StringUtil.repeatSymbol(' ', Math.max(0, spacing));
      }
    }
  }

  @Override
  protected boolean doCharDeleted(char c, PsiFile file, Editor editor) {
    if (myReplacement == null) {
      return false;
    }

    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    int endOffset = CharArrayUtil.shiftForward(document.getImmutableCharSequence(), caretModel.getOffset(), " \t");

    document.replaceString(myStartOffset, endOffset, myReplacement);
    caretModel.moveToOffset(myStartOffset + myReplacement.length());

    return true;
  }

  private static int getWidth(@NotNull String indent, int tabSize) {
    int width = 0;
    for (int i = 0; i < indent.length(); i++) {
      char c = indent.charAt(i);
      switch (c) {
        case '\t':
          width = tabSize * (width / tabSize + 1);
          break;
        default:
          LOG.error("Unexpected whitespace character: " + ((int)c));
        case ' ':
          width++;
      }
    }
    return width;
  }
}
