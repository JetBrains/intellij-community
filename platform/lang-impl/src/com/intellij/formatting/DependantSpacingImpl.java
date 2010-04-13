/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * Extends {@link SpacingImpl} in order to add notion of dependency range.
 * <p/>
 * <code>'Dependency'</code> here affect {@link #getMinLineFeeds() minLineFieeds} property value. See property contract for more details.
 */
public class DependantSpacingImpl extends SpacingImpl {
  private final TextRange myDependency;
  private static final int DEPENDENCE_CONTAINS_LF_MASK = 0x10;
  private static final int LF_WAS_USED_MASK = 0x20;

  public DependantSpacingImpl(final int minSpaces,
                              final int maxSpaces,
                              TextRange dependency,
                              final boolean keepLineBreaks,
                              final int keepBlankLines) {
    super(minSpaces, maxSpaces, 0, false, false, keepLineBreaks, keepBlankLines, false, 0);
    myDependency = dependency;
  }

  /**
   * @return    <code>1</code> if dependency has line feeds; <code>0</code> otherwise
   */
  int getMinLineFeeds() {
    if ((myFlags & DEPENDENCE_CONTAINS_LF_MASK) != 0) {
      return 1;
    }
    else {
      return 0;
    }
  }

  public void refresh(FormatProcessor formatter) {
    final boolean value = wasLFUsed() || formatter.containsLineFeeds(myDependency);
    if (value) myFlags |= DEPENDENCE_CONTAINS_LF_MASK;
    else myFlags &= ~DEPENDENCE_CONTAINS_LF_MASK;
  }

  public TextRange getDependency() {
    return myDependency;
  }

  public final void setLFWasUsed(final boolean value) {
    if (value) myFlags |= LF_WAS_USED_MASK;
    else myFlags &=~ LF_WAS_USED_MASK;
  }

  public final boolean wasLFUsed() {
    return (myFlags & LF_WAS_USED_MASK) != 0;
  }

  @Override
  public String toString() {
    return "<DependantSpacing: minSpaces=" + getMinSpaces() + " maxSpaces=" + getMaxSpaces() + " minLineFeeds=" + getMinLineFeeds() + " dep=" +
           myDependency + ">";
  }
}
