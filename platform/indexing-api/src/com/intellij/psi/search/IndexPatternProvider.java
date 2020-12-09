// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;

/**
 * Provider of regular expression patterns the occurrences of which in the comments of
 * source code files are indexed.
 * Register via {@code com.intellij.indexPatternProvider} extension point.
 *
 * @author yole
 * @see com.intellij.psi.search.searches.IndexPatternSearch
 */
public interface IndexPatternProvider {
  ExtensionPointName<IndexPatternProvider> EP_NAME = new ExtensionPointName<>("com.intellij.indexPatternProvider");

  Topic<PropertyChangeListener> INDEX_PATTERNS_CHANGED = new Topic<>("index patterns changed", PropertyChangeListener.class, Topic.BroadcastDirection.NONE);

  /**
   * The property the change of which should be reported to the property change listener
   * when the list of index patterns is changed.
   *
   * @see #INDEX_PATTERNS_CHANGED
   */
  @NonNls String PROP_INDEX_PATTERNS = "indexPatterns";

  /**
   * Returns index patterns the occurrences of which should be indexed.
   */
  IndexPattern @NotNull [] getIndexPatterns();
}
