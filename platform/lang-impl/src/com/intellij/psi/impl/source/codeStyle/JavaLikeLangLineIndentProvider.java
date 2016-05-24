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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.formatting.Indent.*;
import static com.intellij.formatting.Indent.Type.*;

/**
 * A base class Java-like language line indent provider. If JavaLikeLangLineIndentProvider is unable to calculate
 * the indentation, it forwards the request to FormatterBasedLineIndentProvider.
 */
public abstract class JavaLikeLangLineIndentProvider extends FormatterBasedLineIndentProvider {
  
  protected enum JavaLikeElement implements SemanticEditorPosition.SyntaxElement {
    Whitespace,
    Semicolon,
    BlockOpeningBrace,
    BlockClosingBrace,
    ArrayOpeningBracket
  }
  
  
  @Nullable
  @Override
  public String getLineIndent(@NotNull Project project, @NotNull Editor editor, Language language, int offset) {
    Type indent = getIndent(editor, offset);
    if (indent != null){
      return getIndentString(editor, offset, indent);
    }
    return super.getLineIndent(project, editor, language, offset);
  }
  
  @Nullable
  protected Type getIndent(@NotNull Editor editor, int offset) {
    if (offset > 0) {
      offset --;
      if (getPosition(editor, offset).matchesRule(
        position -> position.isAt(JavaLikeElement.Whitespace) &&
                    position.isAtMultiline())) {
        if (getPosition(editor, offset).matchesRule(
          position -> position
            .before()
            .beforeOptional(JavaLikeElement.Semicolon)
            .beforeOptional(JavaLikeElement.Whitespace)
            .isAt(JavaLikeElement.BlockClosingBrace))) {
          return NONE;
        }
        else if (getPosition(editor, offset).matchesRule(
          position -> position.before().isAt(JavaLikeElement.ArrayOpeningBracket) 
        )) {
          return CONTINUATION;
        }
        else if (getPosition(editor, offset).matchesRule(
          position -> position.before().isAt(JavaLikeElement.BlockOpeningBrace) 
        )) {
          return NORMAL;
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
  
  protected abstract SemanticEditorPosition.SyntaxElement mapType(@NotNull IElementType tokenType);
  
  @Nullable
  private static String getIndentString(@NotNull Editor editor, int offset, Type indentType) {
    Project project = editor.getProject();
    if (project != null) {
      Document document = editor.getDocument();
      String lastLineIndent = getLastLineIndent(document.getCharsSequence(), offset);
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file != null) {
        CommonCodeStyleSettings.IndentOptions options = CodeStyleSettingsManager.getSettings(project).getIndentOptionsByFile(file);
        return 
          lastLineIndent + new IndentInfo(0, indentTypeToSize(indentType, options), 0, false).generateNewWhiteSpace(options);
      }
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
  private static String getLastLineIndent(@NotNull CharSequence docChars, int offset) {
    if (offset > 0) {
      int indentStart = CharArrayUtil.shiftBackward(docChars, offset - 1, " \t\n\r");
      if (indentStart > 0) {
        indentStart = CharArrayUtil.shiftBackwardUntil(docChars, indentStart, "\n") + 1;
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
}
