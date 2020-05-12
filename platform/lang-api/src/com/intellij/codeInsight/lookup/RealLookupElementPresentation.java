package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated there's no difference with {@link LookupElementPresentation}.
 * To speed up completion by delaying rendering more expensive parts, implement {@link LookupElement#getExpensiveRenderer()}.
 */
@SuppressWarnings("unused")
@ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
@Deprecated
public class RealLookupElementPresentation extends LookupElementPresentation {

  /**
   * @deprecated lookup element's presentation shouldn't depend on the available space. Please use as long strings as you like,
   * the platform will trim them for you as needed.
   */
  @Deprecated
  public boolean hasEnoughSpaceFor(@Nullable String text, boolean bold) {
    return true;
  }
}
