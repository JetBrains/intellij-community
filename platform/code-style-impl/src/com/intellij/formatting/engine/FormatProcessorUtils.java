// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.engine;

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

final class FormatProcessorUtils {


  private static int calcShift(final @NotNull IndentInside oldIndent,
                               final @NotNull IndentInside newIndent,
                               final @NotNull CommonCodeStyleSettings.IndentOptions options)
  {
    if (oldIndent.equals(newIndent)) return 0;
    return newIndent.getSpacesCount(options) - oldIndent.getSpacesCount(options);
  }

  static int replaceWhiteSpace(final FormattingModel model,
                               final @NotNull LeafBlockWrapper block,
                               int shift,
                               final CharSequence _newWhiteSpace,
                               final CommonCodeStyleSettings.IndentOptions options
  ) {
    final WhiteSpace whiteSpace = block.getWhiteSpace();
    final TextRange textRange = whiteSpace.getTextRange();
    final TextRange wsRange = textRange.shiftRight(shift);
    final String newWhiteSpace = _newWhiteSpace.toString();
    TextRange newWhiteSpaceRange = model instanceof FormattingModelEx
                                   ? ((FormattingModelEx) model).replaceWhiteSpace(wsRange, block.getNode(), newWhiteSpace)
                                   : model.replaceWhiteSpace(wsRange, newWhiteSpace);

    shift += newWhiteSpaceRange.getLength() - textRange.getLength();

    if (block.isLeaf() && whiteSpace.containsLineFeeds() && block.containsLineFeeds()) {
      final TextRange currentBlockRange = block.getTextRange().shiftRight(shift);

      IndentInside oldBlockIndent = whiteSpace.getInitialLastLineIndent();
      IndentInside whiteSpaceIndent = IndentInside.createIndentOn(IndentInside.getLastLine(newWhiteSpace));
      final int shiftInside = calcShift(oldBlockIndent, whiteSpaceIndent, options);

      if (shiftInside != 0 || !oldBlockIndent.equals(whiteSpaceIndent)) {
        final TextRange newBlockRange = model.shiftIndentInsideRange(block.getNode(), currentBlockRange, shiftInside);
        shift += newBlockRange.getLength() - block.getLength();
      }
    }
    return shift;
  }

}
