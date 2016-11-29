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
package com.intellij.psi.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;

/**
 * Provider of regular expression patterns the occurrences of which in the comments of
 * source code files are indexed by IDEA. Implementations of this interface need to be registered
 * as extensions for the <code>indexPatternProvider</code> extension point.
 *
 * @author yole
 * @since 5.1
 * @see com.intellij.psi.search.searches.IndexPatternSearch
 */
public interface IndexPatternProvider {
  ExtensionPointName<IndexPatternProvider> EP_NAME = ExtensionPointName.create("com.intellij.indexPatternProvider");

  Topic<PropertyChangeListener> INDEX_PATTERNS_CHANGED = new Topic<>("index patterns changed", PropertyChangeListener.class);

  /**
   * The property the change of which should be reported to the property change listener
   * when the list of index patterns is changed.
   *
   * @see #addPropertyChangeListener(java.beans.PropertyChangeListener)
   */
  @NonNls String PROP_INDEX_PATTERNS = "indexPatterns";

  /**
   * Returns the list of index patterns the occurrences of which should be indexed by IDEA.
   *
   * @return the array of index patterns
   */
  @NotNull IndexPattern[] getIndexPatterns();
}
