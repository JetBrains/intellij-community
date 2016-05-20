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

import com.intellij.formatting.Indent;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A base class Java-like language line indent provider. If JavaLikeLangLineIndentProvider is unable to calculate
 * the indentation, it forwards the request to FormatterBasedLineIndentProvider.
 */
public abstract class JavaLikeLangLineIndentProvider extends FormatterBasedLineIndentProvider {
  
  protected enum JavaLikeElement implements SemanticEditorPosition.SyntaxElement {
    Whitespace,
    Semicolon,
    BlockClosingBrace
  }
  
  
  @Nullable
  @Override
  public String getLineIndent(@NotNull Project project, @NotNull Editor editor, int offset) {
    Indent.Type indent = getIndent(editor, offset);
    if (indent == Indent.Type.NONE) return null;
    return super.getLineIndent(project, editor, offset);
  }

  @Override
  public final boolean isSuitableFor(@Nullable PsiFile file) {
    if (file != null) {
      return isSuitableForLanguage(file.getLanguage());
    }
    return false;
  }
  
  protected abstract boolean isSuitableForLanguage(@NotNull Language language);
  
  @Nullable
  protected Indent.Type getIndent(@NotNull Editor editor, int offset) {
    if (offset > 0) {
      if (matchesRule(editor, offset - 1,
                 position -> position.isAt(JavaLikeElement.Whitespace) &&
                            position.isAtMultiline()
                            && position
                              .before()
                              .beforeOptional(JavaLikeElement.Semicolon)
                              .beforeOptional(JavaLikeElement.Whitespace)
                              .isAt(JavaLikeElement.BlockClosingBrace)
      )) {
        return Indent.Type.NONE;
      }
    }
    return null;
  }

  private boolean matchesRule(@NotNull Editor editor, int offset, @NotNull Rule rule) {
    SemanticEditorPosition editorPosition = new SemanticEditorPosition((EditorEx)editor, offset) {
      @Override
      public SyntaxElement map(@NotNull IElementType elementType) {
        return mapType(elementType);
      }
    };
    return rule.check(editorPosition);
  }
  
  protected abstract SemanticEditorPosition.SyntaxElement mapType(@NotNull IElementType tokenType);
  
  private interface Rule {
    boolean check(SemanticEditorPosition wrapper);
  }
}
