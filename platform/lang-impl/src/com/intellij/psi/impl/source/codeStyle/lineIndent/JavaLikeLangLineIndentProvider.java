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
package com.intellij.psi.impl.source.codeStyle.lineIndent;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.SemanticEditorPosition;
import com.intellij.psi.impl.source.codeStyle.SemanticEditorPosition.SyntaxElement;
import com.intellij.psi.impl.source.codeStyle.lineIndent.IndentCalculator.BaseLineOffsetCalculator;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.formatting.Indent.Type;
import static com.intellij.formatting.Indent.Type.*;
import static com.intellij.psi.impl.source.codeStyle.lineIndent.JavaLikeLangLineIndentProvider.JavaLikeElement.*;

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
    ForKeyword,
    BlockComment,
    DocBlockStart,
    DocBlockEnd
  }
  
  
  @Nullable
  @Override
  public String getLineIndent(@NotNull Project project, @NotNull Editor editor, Language language, int offset) {
    IndentCalculator indentCalculator = getIndent(project, editor, language, offset);
    if (indentCalculator != null){
      return indentCalculator.getIndentString(getPosition(editor, offset));
    }
    return super.getLineIndent(project, editor, language, offset);
  }
  
  @Nullable
  protected IndentCalculator getIndent(@NotNull Project project, @NotNull Editor editor, @Nullable Language language, int offset) {
    if (offset > 0) {
      offset--;
      if (getPosition(editor, offset).matchesRule(
        position -> position.isAt(Whitespace) &&
                    position.isAtMultiline())) {
        IndentCalculatorFactory myFactory = new IndentCalculatorFactory(project, editor);
        if (getPosition(editor, offset).matchesRule(
          position -> position
            .before()
            .beforeOptional(Semicolon)
            .beforeOptional(Whitespace)
            .isAt(BlockClosingBrace))) {
          return myFactory.createIndentCalculator(getBlockIndentType(project, language), IndentCalculator.LINE_BEFORE);
        }
        else if (getPosition(editor, offset).matchesRule(
          position -> position.before().isAt(ArrayOpeningBracket) || position.isAt(LeftParenthesis)
        )) {
          return myFactory.createIndentCalculator(CONTINUATION, IndentCalculator.LINE_BEFORE);
        }
        else if (getPosition(editor, offset).matchesRule(
          position -> position.before().isAt(BlockOpeningBrace)
        )) {
          return myFactory.createIndentCalculator(getIndentTypeInBlock(project, language), position -> {
            position.before().beforeOptional(Whitespace);
            return getOffsetBeforeOptionalParentheses(position);
          });
        }
        else if (getPosition(editor, offset).matchesRule(
          position -> position.before().isAt(Colon) && position.isAfterOnSameLine(SwitchCase, SwitchDefault)
        ) || getPosition(editor, offset).matchesRule(
          position -> position.before().isAt(ElseKeyword)
        )) {
          return myFactory.createIndentCalculator(NORMAL, IndentCalculator.LINE_BEFORE);
        }
        else if (getPosition(editor, offset).matchesRule(
          position -> position.before().isAt(BlockComment)
        )) {
          return myFactory.createIndentCalculator(NONE, position -> position.findStartOf(BlockComment));
        }
        else if (getPosition(editor, offset).matchesRule(
          position -> position.before().isAt(DocBlockEnd)
        )) {
          return myFactory.createIndentCalculator(NONE, position -> position.findStartOf(DocBlockStart));
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
                final int controlKeywordOffset = position.getStartOffset();
                Type indentType = getPosition(editor, offsetAfterParen).afterOptional(Whitespace).isAt(BlockOpeningBrace) ? NONE : NORMAL;
                return myFactory.createIndentCalculator(indentType, baseLineOffset -> controlKeywordOffset);
              }
            }
          }
        }
      }
    }
    return null;
  }
  
  
  private static int getOffsetBeforeOptionalParentheses(@NotNull SemanticEditorPosition position) {
    position.beforeOptional(Whitespace);
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

  
  private boolean isOnSeparateLine(@NotNull Editor editor, @NotNull SyntaxElement element, int offset) {
    SemanticEditorPosition position = getPosition(editor, offset);
    position.beforeOptional(Whitespace);
    if (position.isAt(element)) {
      position.before();
      if (position.isAtEnd() || position.isAt(Whitespace) && position.isAtMultiline()) return true;
    }
    return false;
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
  
  
  
  public static class IndentCalculatorFactory {
    private Project myProject;
    private Editor myEditor;

    public IndentCalculatorFactory(Project project, Editor editor) {
      myProject = project;
      myEditor = editor;
    }
    
    @Nullable
    public IndentCalculator createIndentCalculator(@Nullable Type indentType, @Nullable BaseLineOffsetCalculator baseLineOffsetCalculator) {
      return indentType != null ?
             new IndentCalculator(myProject, myEditor,
                                  baseLineOffsetCalculator != null ? baseLineOffsetCalculator : IndentCalculator.LINE_BEFORE, indentType) 
                                : null;
    }
  }

  @Override
  public final boolean isSuitableFor(@Nullable Language language) {
    return language != null && isSuitableForLanguage(language);
  }
  
  public abstract boolean isSuitableForLanguage(@NotNull Language language);
}
