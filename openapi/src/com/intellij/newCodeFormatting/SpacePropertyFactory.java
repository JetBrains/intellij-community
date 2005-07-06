package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;

interface SpacePropertyFactory {
  public SpaceProperty createSpaceProperty(int minOffset,
                                                      int maxOffset,
                                                      int minLineFeeds,
                                                      boolean keepLineBreaks,
                                                      int keepBlankLines);

  public SpaceProperty getReadOnlySpace();

  public SpaceProperty createDependentLFProperty(int minOffset,
                                                          int maxOffset,
                                                          TextRange dependance,
                                                          boolean keepLineBreaks,
                                                          int keepBlankLines);

  public SpaceProperty createSafeSpace(boolean keepLineBreaks,
                                                int keepBlankLines);

  public SpaceProperty createKeepingFirstLineSpaceProperty(final int minSpace,
                                                                    final int maxSpace,
                                                                    final boolean keepLineBreaks,
                                                                    final int keepBlankLines);

}
