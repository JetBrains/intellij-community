package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;

interface SpacingFactory {
  public Spacing createSpacing(int minOffset,
                               int maxOffset,
                               int minLineFeeds,
                               boolean keepLineBreaks,
                               int keepBlankLines);

  public Spacing getReadOnlySpacing();

  public Spacing createDependentLFSpacing(int minOffset,
                                          int maxOffset,
                                          TextRange dependance,
                                          boolean keepLineBreaks,
                                          int keepBlankLines);

  public Spacing createSafeSpacing(boolean keepLineBreaks,
                                   int keepBlankLines);

  public Spacing createKeepingFirstLineSpacing(final int minSpace,
                                               final int maxSpace,
                                               final boolean keepLineBreaks,
                                               final int keepBlankLines);

}
