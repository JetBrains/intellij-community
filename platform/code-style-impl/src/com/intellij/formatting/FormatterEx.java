// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class FormatterEx implements Formatter {

  public static FormatterEx getInstance() {
    return (FormatterEx)Formatter.getInstance();
  }

  public abstract void format(FormattingModel model,
                              CodeStyleSettings settings,
                              CommonCodeStyleSettings.IndentOptions indentOptions,
                              FormatTextRanges affectedRanges) throws IncorrectOperationException;

  public abstract int adjustLineIndent(final FormattingModel psiBasedFormattingModel,
                                       final CodeStyleSettings settings,
                                       final CommonCodeStyleSettings.IndentOptions indentOptions,
                                       final int offset,
                                       final TextRange affectedRange) throws IncorrectOperationException;

  @Nullable
  public abstract String getLineIndent(final FormattingModel psiBasedFormattingModel,
                                       final CodeStyleSettings settings,
                                       final CommonCodeStyleSettings.IndentOptions indentOptions,
                                       final int offset,
                                       final TextRange affectedRange);

  @Nullable
  @ApiStatus.Internal
  public abstract List<String> getLineIndents(final FormattingModel model,
                                              final CodeStyleSettings settings,
                                              final CommonCodeStyleSettings.IndentOptions indentOptions);


  public abstract void adjustLineIndentsForRange(final FormattingModel model,
                                                 final CodeStyleSettings settings,
                                                 final CommonCodeStyleSettings.IndentOptions indentOptions,
                                                 final TextRange rangeToAdjust);

  public abstract void formatAroundRange(final FormattingModel model,
                                         final CodeStyleSettings settings,
                                         final PsiFile file,
                                         final TextRange textRange);

  public abstract void setProgressTask(@NotNull FormattingProgressCallback progressIndicator);

  /**
   * Calculates minimum spacing, allowed by formatting model (in columns) for a block starting at given offset,
   * relative to its previous sibling block.
   * Returns {@code -1}, if required block cannot be found at provided offset,
   * or spacing cannot be calculated due to some other reason.
   */
  public abstract int getSpacingForBlockAtOffset(FormattingModel model, int offset);

  /**
   * Calculates minimum number of line feeds that should precede block starting at given offset, as dictated by formatting model.
   * Returns {@code -1}, if required block cannot be found at provided offset,
   * or spacing cannot be calculated due to some other reason.
   */
  public abstract int getMinLineFeedsBeforeBlockAtOffset(FormattingModel model, int offset);


  public static FormatterEx getInstanceEx() {
    return getInstance();
  }

}
