/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class AutoFormatTypedHandler extends TypedActionHandlerBase {
  private static boolean myIsEnabledInTests = false;
  
  private static char[] NO_SPACE_AFTER = { 
    '+', '-', '*', '/', '%', '&', '^', '|', '<', '>', '!', '=', ' ' 
  };
  
  private char myLastTypedChar;
  private int myLastTypedOffset;
  private long myLastModificationStamp;

  public AutoFormatTypedHandler(@Nullable TypedActionHandler originalHandler) {
    super(originalHandler);
  }
  
  private static boolean isEnabled() {
    return Registry.is("editor.reformat.on.typing") 
           || myIsEnabledInTests && ApplicationManager.getApplication().isUnitTestMode();
  }
  
  @TestOnly
  public static void setEnabledInTests(boolean value) {
    myIsEnabledInTests = value;
  }
  
  @Override
  public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    if (!isEnabled()) {
      executeOriginalHandler(editor, charTyped, dataContext);
      return;
    }
    
    if (isInsertSpaceAtCaret(editor, charTyped, dataContext)) {
      EditorModificationUtil.insertStringAtCaret(editor, " ");
    }
    
    executeOriginalHandler(editor, charTyped, dataContext);
    
    myLastTypedChar = charTyped;
    myLastTypedOffset = editor.getCaretModel().getOffset();
    myLastModificationStamp = editor.getDocument().getModificationStamp();
  }

  private boolean isInsertSpaceAtCaret(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    if (!isSpaceAroundAssignment(editor, dataContext)) {
      return false;
    }
    
    int caretOffset = editor.getCaretModel().getOffset();
    CharSequence text = editor.getDocument().getImmutableCharSequence();

    boolean insertBeforeEq = charTyped == '=' && isInsertSpaceBeforeEq(caretOffset, text);
    boolean insertAfterEq = myLastTypedChar == '=' && isInsertSpaceBeforeNewChar(charTyped) 
                            && isSameDocumentAsPrevious(editor);
    
    return (insertBeforeEq || insertAfterEq) && !isInsideStringLiteral(editor);
  }

  private static boolean isInsertSpaceBeforeNewChar(char charTyped) {
    return charTyped != '=' && charTyped != ' ';
  }

  private static boolean isInsideStringLiteral(Editor editor) {
    if (editor.getDocument().getTextLength() == 0) return false;
    
    if (editor instanceof EditorEx) {
      int caretOffset = editor.getCaretModel().getOffset();
      HighlighterIterator lexer = ((EditorEx)editor).getHighlighter().createIterator(caretOffset);
      IElementType token = lexer.getTokenType();
      if ("STRING_LITERAL".equals(token.toString())) {
        return true;
      }
    }
    
    return false;
  }

  private void executeOriginalHandler(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    if (myOriginalHandler != null) myOriginalHandler.execute(editor, charTyped, dataContext);
  }

  private boolean isSameDocumentAsPrevious(Editor editor) {
    return editor.getDocument().getModificationStamp() == myLastModificationStamp 
           && editor.getCaretModel().getOffset() == myLastTypedOffset;
  }

  private static boolean isInsertSpaceBeforeEq(int caretOffset, CharSequence text) {
    if (caretOffset == 0) return false;
    char charBefore = text.charAt(caretOffset - 1);

    for (char c : NO_SPACE_AFTER) {
      if (c == charBefore) {
        return false;
      }
    }
    
    return true;
  }

  private static boolean isSpaceAroundAssignment(Editor editor, DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    PsiFile file = project == null ? null : PsiUtilBase.getPsiFileInEditor(editor, project);
    if (file != null) {
      Language language = file.getLanguage();
      CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
      CommonCodeStyleSettings common = settings.getCommonSettings(language);
      return common.SPACE_AROUND_ASSIGNMENT_OPERATORS;
    }
    return false;
  }
  
}
