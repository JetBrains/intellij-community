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

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
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
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public class AutoFormatTypedHandler extends TypedActionHandlerBase {
  private static boolean myIsEnabledInTests;
  
  private static final char[] NO_SPACE_AFTER = {
    '+', '-', '*', '/', '%', '&', '^', '|', '<', '>', '!', '=', ' ' 
  };

  private static final List<IElementType> COMPLEX_ASSIGNMENTS = ContainerUtil.newArrayList(
    JavaTokenType.PLUSEQ, JavaTokenType.MINUSEQ,
    JavaTokenType.ASTERISKEQ, JavaTokenType.DIVEQ,
    JavaTokenType.PERCEQ,
    JavaTokenType.ANDEQ, JavaTokenType.XOREQ, JavaTokenType.OREQ,
    JavaTokenType.LTLTEQ, JavaTokenType.GTGTEQ
  );
  
  public AutoFormatTypedHandler(@Nullable TypedActionHandler originalHandler) {
    super(originalHandler);
  }

  private static boolean isEnabled(Editor editor) {
    boolean isEnabled = myIsEnabledInTests && ApplicationManager.getApplication().isUnitTestMode() 
                        || Registry.is("editor.reformat.on.typing");
    
    if (!isEnabled) {
      return false;
    }
    
    Project project = editor.getProject();
    Language language = null;
    if (project != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file != null) {
        language = file.getLanguage();
      }
    }

    return language == JavaLanguage.INSTANCE;
  }
  
  @TestOnly
  public static void setEnabledInTests(boolean value) {
    myIsEnabledInTests = value;
  }
  
  @Override
  public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    if (!isEnabled(editor)) {
      executeOriginalHandler(editor, charTyped, dataContext);
      return;
    }
    
    if (isInsertSpaceAtCaret(editor, charTyped, dataContext)) {
      EditorModificationUtil.insertStringAtCaret(editor, " ");
    }
    
    executeOriginalHandler(editor, charTyped, dataContext);
  }

  private static boolean isInsertSpaceAtCaret(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    if (!isSpaceAroundAssignment(editor, dataContext)) {
      return false;
    }
    
    int caretOffset = editor.getCaretModel().getOffset();
    CharSequence text = editor.getDocument().getImmutableCharSequence();
    
    HighlighterIterator lexerIterator = createLexerIterator(editor, caretOffset);
    if (lexerIterator == null || lexerIterator.getTokenType() == JavaTokenType.STRING_LITERAL) {
      return false;
    }

    boolean insertBeforeEq = charTyped == '=' && isInsertSpaceBeforeEq(caretOffset, text);
    boolean insertAfterEq = caretOffset > 0 && caretOffset - 1 < text.length() && text.charAt(caretOffset - 1) == '=' 
                            && isAssignmentOperator(lexerIterator) && isInsertSpaceAfterEq(charTyped); 
    
    return (insertBeforeEq || insertAfterEq);
  }

  private static boolean isAssignmentOperator(HighlighterIterator iterator) {
    IElementType type = iterator.getTokenType();
    if (type == TokenType.WHITE_SPACE) {
      iterator.retreat();
      type = iterator.getTokenType();
    }

    if (COMPLEX_ASSIGNMENTS.indexOf(type) >= 0) {
      return true;
    }

    if (type == JavaTokenType.EQ) {
      iterator.retreat();
      type = iterator.getTokenType();
      if (type == JavaTokenType.GT) {
        iterator.retreat();
        type = iterator.getTokenType();
        if (type == JavaTokenType.GT) {
          return true;
        }
      }
      
      else if (type == TokenType.WHITE_SPACE || type == JavaTokenType.IDENTIFIER) {
        return true;
      }
    }
    
    return false;
  }

  private static boolean isInsertSpaceAfterEq(char charTyped) {
    return charTyped != '=' && charTyped != ' ';
  }

  private static HighlighterIterator createLexerIterator(Editor editor, int offset) {
    if (editor.getDocument().getTextLength() == 0) return null;
    return editor instanceof EditorEx 
           ? ((EditorEx)editor).getHighlighter().createIterator(offset) 
           : null;
  }

  private void executeOriginalHandler(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
    if (myOriginalHandler != null) myOriginalHandler.execute(editor, charTyped, dataContext);
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
      CodeStyleSettings settings = CodeStyle.getSettings(editor);
      CommonCodeStyleSettings common = settings.getCommonSettings(language);
      return common.SPACE_AROUND_ASSIGNMENT_OPERATORS;
    }
    return false;
  }
  
}
