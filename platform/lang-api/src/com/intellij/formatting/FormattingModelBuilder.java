/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows a custom language plugin to build a formatting model for a file in the language, or
 * for a portion of a file.
 * A formatting model defines how a file is broken into non-whitespace blocks and different
 * types of whitespace (alignment, indents and wraps) between them.
 * <p>For certain aspects of the custom formatting to work properly, it is recommended to use TokenType.WHITE_SPACE
 * as the language's whitespace tokens. See {@link com.intellij.lang.ParserDefinition}
 *
 * @see com.intellij.lang.LanguageFormatting
 * @see FormattingModelProvider#createFormattingModelForPsiFile(PsiFile, Block, CodeStyleSettings)
 */

public interface FormattingModelBuilder {
  
  /**
   * Requests building the formatting model for a section of the file containing
   * the specified PSI element and its children.
   *
   * @param element  the top element for which formatting is requested.
   * @param settings the code style settings used for formatting.
   * @return the formatting model for the file.
   */
  @NotNull
  FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings);

  /**
   * Returns the TextRange which should be processed by the formatter in order to calculate the
   * indent for a new line when a line break is inserted at the specified offset.
   *
   * @param file   the file in which the line break is inserted.
   * @param offset the line break offset.
   * @param elementAtOffset the parameter at {@code offset}
   * @return the range to reformat, or null if the default range should be used
   */
  @Nullable
  default TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return null;
  }
}
