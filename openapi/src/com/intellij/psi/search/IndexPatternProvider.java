/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.search;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;

/**
 * Provider of regular expression patterns the occurrences of which in the comments of
 * source code files are indexed by IDEA.
 *
 * @author yole
 */
public interface IndexPatternProvider {
  @NonNls String PROP_INDEX_PATTERNS = "indexPatterns";

  /**
   * Returns the list of index patterns the occurrences of which should be indexed by IDEA.
   *
   * @return the array of index patterns
   */
  @NotNull IndexPattern[] getIndexPatterns();

  /**
   * Adds a listener which is notified when the set of index patterns provided by this provider
   * changes.
   *
   * @param listener the listener to add.
   */
  void addPropertyChangeListener(PropertyChangeListener listener);

  /**
   * Removes a listener which is notified when the set of index patterns provided by this provider
   * changes.
   *
   * @param listener the listener to remove.
   */
  void removePropertyChangeListener(PropertyChangeListener listener);

  void dispatchPendingEvent(PropertyChangeListener listener);

}
