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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElementWeigher;
import org.jetbrains.annotations.NotNull;

public abstract class CompletionSorter {
  public abstract CompletionSorter weighBefore(@NotNull String beforeId, LookupElementWeigher... weighers);

  public abstract CompletionSorter weighAfter(@NotNull String afterId, LookupElementWeigher... weighers);

  public abstract CompletionSorter weigh(LookupElementWeigher weigher);

  public static CompletionSorter emptySorter() {
    return CompletionService.getCompletionService().emptySorter();
  }

  public static CompletionSorter defaultSorter(CompletionParameters parameters, PrefixMatcher matcher) {
    return CompletionService.getCompletionService().defaultSorter(parameters, matcher);
  }

}
