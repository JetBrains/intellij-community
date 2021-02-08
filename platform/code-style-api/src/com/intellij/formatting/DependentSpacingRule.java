// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Holds settings that should be used if
 * {@link Spacing#createDependentLFSpacing(int, int, TextRange, boolean, int, DependentSpacingRule) dependent spacing}
 * target region changes its 'contains line feeds' status.
 */
public final class DependentSpacingRule {
  public enum Anchor {
    MIN_LINE_FEEDS, MAX_LINE_FEEDS
  }

  public enum Trigger {
    HAS_LINE_FEEDS, DOES_NOT_HAVE_LINE_FEEDS
  }

  public static final DependentSpacingRule DEFAULT =
    new DependentSpacingRule(Trigger.HAS_LINE_FEEDS).registerData(Anchor.MIN_LINE_FEEDS, 1);

  private final Object2IntMap<Anchor> myData = new Object2IntOpenHashMap<>();

  @NotNull private final Trigger myTrigger;

  public DependentSpacingRule(@NotNull Trigger trigger) {
    myTrigger = trigger;
  }

  @NotNull
  public Trigger getTrigger() {
    return myTrigger;
  }

  /**
   * Allows to register given data for the given anchor within the current rule.
   *
   * @param anchor  target anchor
   * @param data    data to register for the given anchor
   * @see #getData(Anchor)
   */
  public DependentSpacingRule registerData(@NotNull Anchor anchor, int data) {
    myData.put(anchor, data);
    return this;
  }

  /**
   * @param anchor  target data anchor
   * @return        {@code true} if there is a data registered for the given anchor within the current rule;
   *                {@code false} otherwise
   */
  public boolean hasData(@NotNull Anchor anchor) {
    return myData.containsKey(anchor);
  }

  /**
   * Allows to retrieve data associated with the given anchor.
   *
   * @param anchor  target anchor
   * @return data associated for the given anchor
   * @throws IllegalArgumentException   if no data is registered for the given anchor
   *                                    (use {@link #hasData(Anchor)} for the preliminary examination)
   */
  public int getData(@NotNull Anchor anchor) throws IllegalArgumentException {
    if (!myData.containsKey(anchor)) {
      throw new IllegalArgumentException(String.format(
        "No data is registered for the dependent spacing rule %s. Registered: %s", anchor, myData
      ));
    }
    return myData.getInt(anchor);
  }
}
