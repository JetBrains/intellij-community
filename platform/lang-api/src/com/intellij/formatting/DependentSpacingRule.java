/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.TextRange;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Holds settings that should be used if
 * {@link Spacing#createDependentLFSpacing(int, int, TextRange, boolean, int, DependentSpacingRule) dependent spacing}
 * target region changes its 'contains line feeds' status.
 * 
 * @author Denis Zhdanov
 * @since 6/28/12 1:08 PM
 */
public class DependentSpacingRule {

  public enum Anchor {
    MIN_LINE_FEEDS, MAX_LINE_FEEDS
  }

  public enum Trigger {
    HAS_LINE_FEEDS, DOES_NOT_HAVE_LINE_FEEDS
  }

  public static final DependentSpacingRule DEFAULT =
    new DependentSpacingRule(Trigger.HAS_LINE_FEEDS).registerData(Anchor.MIN_LINE_FEEDS, 1);

  private final TObjectIntHashMap<Anchor> myData = new TObjectIntHashMap<>();

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
   * @param <T>     data's type
   * @see #getData(Anchor)
   */
  public DependentSpacingRule registerData(@NotNull Anchor anchor, int data) {
    myData.put(anchor, data);
    return this;
  }

  /**
   * @param anchor  target data anchor
   * @return        <code>true</code> if there is a data registered for the given anchor within the current rule;
   *                <code>false</code> otherwise
   */
  public boolean hasData(@NotNull Anchor anchor) {
    return myData.containsKey(anchor);
  }

  /**
   * Allows to retrieve data associated with the given anchor.
   *
   * @param anchor  target anchor
   * @param <T>     data's type
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
    return myData.get(anchor);
  }
}
