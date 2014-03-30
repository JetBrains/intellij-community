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
import org.jetbrains.annotations.NotNull;

/**
 * Extends {@link SpacingImpl} in order to add notion of dependency range.
 * <p/>
 * <code>'Dependency'</code> here affect {@link #getMinLineFeeds() minLineFieeds} property value. See property contract for more details.
 */
public class DependantSpacingImpl extends SpacingImpl {
  private static final int DEPENDENCE_CONTAINS_LF_MASK      = 0x10;
  private static final int DEPENDENT_REGION_LF_CHANGED_MASK = 0x20;

  @NotNull private final TextRange myDependency;
  @NotNull private final DependentSpacingRule myRule;


  public DependantSpacingImpl(final int minSpaces,
                              final int maxSpaces,
                              @NotNull TextRange dependency,
                              final boolean keepLineBreaks,
                              final int keepBlankLines,
                              @NotNull DependentSpacingRule rule)
  {
    super(minSpaces, maxSpaces, 0, false, false, keepLineBreaks, keepBlankLines, false, 0);
    myDependency = dependency;
    myRule = rule;
  }

  /**
   * @return    <code>1</code> if dependency has line feeds; <code>0</code> otherwise
   */
  @Override
  public int getMinLineFeeds() {
    if (!isTriggered()) {
      return super.getMinLineFeeds();
    }

    if (myRule.hasData(DependentSpacingRule.Anchor.MIN_LINE_FEEDS)) {
      return myRule.getData(DependentSpacingRule.Anchor.MIN_LINE_FEEDS);
    }

    if (myRule.hasData(DependentSpacingRule.Anchor.MAX_LINE_FEEDS)) {
      return myRule.getData(DependentSpacingRule.Anchor.MAX_LINE_FEEDS);
    }
    return super.getMinLineFeeds();
  }

  @Override
  public int getKeepBlankLines() {
    if (!isTriggered() || !myRule.hasData(DependentSpacingRule.Anchor.MAX_LINE_FEEDS)) {
      return super.getKeepBlankLines();
    }

    return 0;
  }

  @Override
  public void refresh(FormatProcessor formatter) {
    if (isDependentRegionChanged()) {
      return;
    }
    final boolean value = formatter.containsLineFeeds(myDependency);
    if (value) myFlags |= DEPENDENCE_CONTAINS_LF_MASK;
    else myFlags &= ~DEPENDENCE_CONTAINS_LF_MASK;
  }

  @NotNull
  public TextRange getDependency() {
    return myDependency;
  }

  /**
   * Allows to answer whether 'contains line feed' status has been changed for the target dependent region during formatting.
   *
   * @return    <code>true</code> if target 'contains line feed' status has been changed for the target dependent region during formatting;
   *            <code>false</code> otherwise
   */
  public final boolean isDependentRegionChanged() {
    return (myFlags & DEPENDENT_REGION_LF_CHANGED_MASK) != 0;
  }

  /**
   * Allows to set {@link #isDependentRegionChanged() 'dependent region changed'} property.
   */
  public final void setDependentRegionChanged() {
    myFlags |= DEPENDENT_REGION_LF_CHANGED_MASK;
    if (getMinLineFeeds() <= 0) myFlags |= DEPENDENCE_CONTAINS_LF_MASK;
    else myFlags &=~DEPENDENCE_CONTAINS_LF_MASK;
  }

  @Override
  public String toString() {
    return "<DependantSpacing: minSpaces=" + getMinSpaces() + " maxSpaces=" + getMaxSpaces() + " minLineFeeds=" + getMinLineFeeds() + " dep=" +
           myDependency + ">";
  }

  private boolean isTriggered() {
    return myRule.getTrigger() == DependentSpacingRule.Trigger.HAS_LINE_FEEDS
           ^ (myFlags & DEPENDENCE_CONTAINS_LF_MASK) == 0;
  }
}
