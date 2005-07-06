package com.intellij.formatting;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;

public abstract class SpaceProperty {
  private static SpacePropertyFactory myFactory;

  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.SpaceProperty");

  static void setFactory(SpacePropertyFactory factory) {
    LOG.assertTrue(myFactory == null);
    if (myFactory == null) {
      myFactory = factory;
    }
  }

  public static SpaceProperty createSpaceProperty(int minOffset,
                                                      int maxOffset,
                                                      int minLineFeeds,
                                                      boolean keepLineBreaks,
                                                      int keepBlankLines){
    return myFactory.createSpaceProperty(minOffset, maxOffset, minLineFeeds, keepLineBreaks, keepBlankLines);
  }

  public static SpaceProperty getReadOnlySpace(){
    return myFactory.getReadOnlySpace();
  }

  public static SpaceProperty createDependentLFProperty(int minOffset,
                                                          int maxOffset,
                                                          TextRange dependance,
                                                          boolean keepLineBreaks,
                                                          int keepBlankLines){
    return myFactory.createDependentLFProperty(minOffset, maxOffset, dependance, keepLineBreaks, keepBlankLines);
  }

  public static SpaceProperty createSafeSpace(boolean keepLineBreaks,
                                                int keepBlankLines){
    return myFactory.createSafeSpace(keepLineBreaks, keepBlankLines);
  }

  public static SpaceProperty createKeepingFirstLineSpaceProperty(final int minSpace,
                                                                    final int maxSpace,
                                                                    final boolean keepLineBreaks,
                                                                    final int keepBlankLines){
    return myFactory.createKeepingFirstLineSpaceProperty(minSpace, maxSpace, keepLineBreaks, keepBlankLines);
  }

}
