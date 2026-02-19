// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link FormattingModelBuilder}
 */
@Deprecated(forRemoval = true)
public interface FormattingModelBuilderEx extends FormattingModelBuilder {
  /**
   * Allows to adjust indent options to used during performing formatting operation on the given ranges of the given file.
   * <p/>
   * Default algorithm is to query given settings for indent options using given file's language as a key.
   *
   * @param file     target file which content is going to be reformatted
   * @param ranges   given file's ranges to reformat
   * @param settings code style settings holder
   * @return indent options to use for the target formatting operation (if any adjustment is required);
   * {@code null} to trigger default algorithm usage
   * @see com.intellij.psi.codeStyle.FileIndentOptionsProvider
   * @deprecated Use {@link com.intellij.psi.codeStyle.FileIndentOptionsProvider} instead.
   */
  @Deprecated(forRemoval = true)
  default @Nullable CommonCodeStyleSettings.IndentOptions getIndentOptionsToUse(@NotNull PsiFile file,
                                                                      @NotNull FormatTextRanges ranges,
                                                                      @NotNull CodeStyleSettings settings) {
    return null;
  }
}
