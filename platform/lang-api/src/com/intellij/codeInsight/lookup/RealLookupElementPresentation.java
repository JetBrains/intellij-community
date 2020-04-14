package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class RealLookupElementPresentation extends LookupElementPresentation {

  @Override
  public boolean isReal() {
    return true;
  }

  /**
   * @deprecated lookup element's presentation shouldn't depend on the available space. Please use as long strings as you like,
   * the platform will trim them for you as needed.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public boolean hasEnoughSpaceFor(@Nullable String text, boolean bold) {
    return true;
  }
}
