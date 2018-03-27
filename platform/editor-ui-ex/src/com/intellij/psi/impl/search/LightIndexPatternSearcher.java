/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

import com.intellij.psi.search.IndexPatternOccurrence;
import com.intellij.psi.search.searches.IndexPatternSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author irengrig
 */
public class LightIndexPatternSearcher extends IndexPatternSearcher {
  @Override
  public boolean execute(@NotNull IndexPatternSearch.SearchParameters queryParameters,
                         @NotNull Processor<IndexPatternOccurrence> consumer) {
    return executeImpl(queryParameters, consumer);
  }
}
