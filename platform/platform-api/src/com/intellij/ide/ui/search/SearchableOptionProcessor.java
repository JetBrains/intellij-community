/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui.search;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A place for {@link SearchableOptionContributor} implementations to feed the searchable options to.
 * 
 * @author peter
 */
public abstract class SearchableOptionProcessor {
  /**
   * Take text that can be found on a setting page, split it into words and add them to the internal setting search index. 
   * 
   * @param text the text that appears on a setting page and can be searched for 
   * @param path for complex settings pages, identifies the subpage where the option is to be found. 
   *             For example, it can be the name of tab on the settings page that should be opened when showing search results.
   *             Can be null for simple configurables.
   * @param hit the string that's presented to the user when showing found results in a list, e.g. in Goto Action. 
   * @param configurableId the id of the topmost configurable containing the search result. See {@link SearchableConfigurable#getId()}
   * @param configurableDisplayName display name of the configurable containing the search result
   * @param applyStemming whether only word stems should be indexed or the full words. Porter stemmer is used. 
   */
  public abstract void addOptions(@NotNull String text,
                                  @Nullable String path,
                                  @Nullable String hit,
                                  @NotNull final String configurableId,
                                  @Nullable final String configurableDisplayName,
                                  boolean applyStemming);

  
}
