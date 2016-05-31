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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.formatting.IndentInfo;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.SemanticEditorPosition.SyntaxElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.formatting.Indent.Type;
import static com.intellij.formatting.Indent.Type.*;
import static com.intellij.psi.impl.source.codeStyle.JavaLikeLangLineIndentProvider.JavaLikeElement.*;

/**
 * A base class Java-like language line indent provider. If JavaLikeLangLineIndentProvider is unable to calculate
 * the indentation, it forwards the request to FormatterBasedLineIndentProvider.
 */
public abstract class JavaLikeLangLineIndentProvider extends FormatterBasedLineIndentProvider {
  
  public enum JavaLikeElement implements SyntaxElement {
    Whitespace,
    Semicolon,
    BlockOpeningBrace,
    BlockClosingBrace,
    ArrayOpeningBracket,
    RightParenthesis,
    LeftParenthesis,
    Colon,
    SwitchCase,
    SwitchDefault,
    ElseKeyword,
    IfKeyword,
    ForKeyword
  }
  
  @Nullable
  @Override
  public String getLineIndent(@NotNull Project project, @NotNull Editor editor, Language language, int offset) {
    Pair<Type,SyntaxElement> indentData = getIndent(project, editor, language, offset);
    if (indentData != null){
      return getIndentString(project, editor, offset, indentData);
    }
    return super.getLineIndent(project, editor, language, offset);
  }
  
  @Nullable
  protected Pair<Type,SyntaxElement> getIndent(@NotNull Project project, @NotNull Editor editor, @Nullable Language language, int offset) {
    if (offset > 0) {
      offset--;
      if (getPosition(editor, offset).matchesRule(
        position -> position.isAt(Whitespace) &&
                    position.isAtMultiline())) {
        if (getPosition(editor, offset).matchesRule(
          position -> position
            .before()
            .beforeOptional(Semicolon)
            .beforeOptional(Whitespace)
            .isAt(BlockClosingBrace))) {
          return createIndentData(getBlockIndentType(project, language), BlockClosingBrace);
        }
        else if (getPosition(editor, offset).matchesRule(
          position -> position.before().isAt(ArrayOpeningBracket)
        )) {
          return createIndentData(CONTINUATION, ArrayOpeningBracket);
        }
        else if (getPosition(editor, offset).matchesRule(
          position -> position.before().isAt(BlockOpeningBrace)
        )) {
          return createIndentData(getIndentTypeInBlock(project, language), BlockOpeningBrace);
        }
        else if (getPosition(editor, offset).matchesRule(
          position -> position.before().isAt(Colon) && position.isAfterOnSameLine(SwitchCase, SwitchDefault)
        )) {
          return createIndentData(NORMAL, SwitchCase);
        }
        else if (getPosition(editor, offset).matchesRule(
          position -> position.before().isAt(ElseKeyword)
        )) {
          return createIndentData(NORMAL, ElseKeyword);
        }
        else {
          SemanticEditorPosition position = getPosition(editor, offset);
          if (position.before().isAt(RightParenthesis)) {
            int offsetAfterParen = position.getStartOffset() + 1;
            position.beforeParentheses(LeftParenthesis, RightParenthesis);
            if (!position.isAtEnd()) {
              position.beforeOptional(Whitespace);
              if (position.isAt(IfKeyword) || position.isAt(ForKeyword)) {
                SyntaxElement element = position.getCurrElement();
                assert element != null;
                Type indentType = getPosition(editor, offsetAfterParen).afterOptional(Whitespace).isAt(BlockOpeningBrace) ? NONE : NORMAL;
                return createIndentData(indentType, element);
              }
            }
          }
        }
      }
    }
    return null;
  }

  protected SemanticEditorPosition getPosition(@NotNull Editor editor, int offset) {
    return new SemanticEditorPosition((EditorEx)editor, offset) {
      @Override
      public SyntaxElement map(@NotNull IElementType elementType) {
        return mapType(elementType);
      }
    };
  }
  
  @Nullable
  protected abstract SyntaxElement mapType(@NotNull IElementType tokenType);
  
  @Nullable
  private String getIndentString(@NotNull Project project,
                                        @NotNull Editor editor,
                                        int offset,
                                        @NotNull Pair<Type, SyntaxElement> indentData) {
    Document document = editor.getDocument();
    String baseIndent = getBaseIndent(editor, indentData.second, offset);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file != null) {
      CommonCodeStyleSettings.IndentOptions options = CodeStyleSettingsManager.getSettings(project).getIndentOptionsByFile(file);
      return
        baseIndent + new IndentInfo(0, indentTypeToSize(indentData.first, options), 0, false).generateNewWhiteSpace(options);
    }
    return null;
  }
  
  private static int indentTypeToSize(@NotNull Type indentType, @NotNull CommonCodeStyleSettings.IndentOptions options) {
    if (indentType == NORMAL) {
      return options.INDENT_SIZE;
    }
    else if (indentType == CONTINUATION) {
      return options.CONTINUATION_INDENT_SIZE;
    }
    return 0;
  }
  
  @NotNull
  private String getBaseIndent(@NotNull Editor editor, @NotNull SyntaxElement afterElement, int offset) {
    CharSequence docChars = editor.getDocument().getCharsSequence();
    if (offset > 0) {
      int indentLineOffset = getOffsetInBaseIndentLine(editor, docChars, afterElement, offset - 1);
      if (indentLineOffset > 0) {
        int indentStart = CharArrayUtil.shiftBackwardUntil(docChars, indentLineOffset, "\n") + 1;
        if (indentStart >= 0) {
          int indentEnd = CharArrayUtil.shiftForward(docChars, indentStart, " \t");
          if (indentEnd > indentStart) {
            return docChars.subSequence(indentStart, indentEnd).toString();
          }
        }
      }
    }
    return "";
  }


  private int getOffsetInBaseIndentLine(@NotNull Editor editor,
                                        @NotNull CharSequence docChars,
                                        @NotNull SyntaxElement afterElement,
                                        int offset) {
    if (BlockOpeningBrace.equals(afterElement) && !isOnSeparateLine(editor, afterElement, offset)) {
      return findStatementStart(editor, afterElement, offset);
    }
    else if (IfKeyword.equals(afterElement) || ForKeyword.equals(afterElement)) {
      return findStatementStart(editor, null, offset);
    }
    return CharArrayUtil.shiftBackward(docChars, offset, " \t\n\r");
  }
  
  
  private boolean isOnSeparateLine(@NotNull Editor editor, @NotNull SyntaxElement element, int offset) {
    SemanticEditorPosition position = getPosition(editor, offset);
    position.beforeOptional(Whitespace);
    if (position.isAt(element)) {
      position.before();
      if (position.isAtEnd() || position.isAt(Whitespace) && position.isAtMultiline()) return true;
    }
    return false;
  }
  
  
  private int findStatementStart(@NotNull Editor editor, @Nullable SyntaxElement afterElement, int offset) {
    SemanticEditorPosition position = getPosition(editor, offset);
    position.beforeOptional(Whitespace);
    if (afterElement != null) {
        position.beforeOptional(afterElement).beforeOptional(Whitespace);
    }
    if (position.isAt(RightParenthesis)) {
      position.beforeParentheses(LeftParenthesis, RightParenthesis);
    }
    while (!position.isAtEnd()) {
      if (position.isAt(Whitespace) && position.isAtMultiline()) {
        return position.after().getStartOffset();
      }
      position.before();
    }
    return -1;
  }
  
  @Nullable
  private static Type getIndentTypeInBlock(@NotNull Project project, @Nullable Language language) {
    if (language != null) {
      CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project).getCommonSettings(language);
      if (settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED) {
        return  settings.METHOD_BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE_SHIFTED ? NONE : null; 
      }
    }
    return NORMAL;
  }
  
  @Nullable
  private static Type getBlockIndentType(@NotNull Project project, @Nullable Language language) {
    if (language != null) {
      CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project).getCommonSettings(language);
      if (settings.BRACE_STYLE == CommonCodeStyleSettings.NEXT_LINE || settings.BRACE_STYLE == CommonCodeStyleSettings.END_OF_LINE) {
        return NONE;
      }
    }
    return null;
  }
  
  @Nullable
  protected static Pair<Type,SyntaxElement> createIndentData(@Nullable Type type, @NotNull SyntaxElement element) {
    return type != null ? Pair.create(type, element) : null;
  }

  @Override
  public final boolean isSuitableFor(@Nullable Language language) {
    return language != null && isSuitableForLanguage(language);
  }
  
  public abstract boolean isSuitableForLanguage(@NotNull Language language);
}
