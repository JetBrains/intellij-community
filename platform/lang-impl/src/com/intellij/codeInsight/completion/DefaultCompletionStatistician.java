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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.statistics.StatisticsInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class DefaultCompletionStatistician extends CompletionStatistician{

  @Override
  public @NotNull Function<@NotNull LookupElement, @Nullable StatisticsInfo> forLocation(@NotNull CompletionLocation location) {
    String context = "completion#" + location.getCompletionParameters().getOriginalFile().getLanguage();
    return element -> new StatisticsInfo(context, element.getLookupString());
  }

  @Override
  public StatisticsInfo serialize(@NotNull final LookupElement element, @NotNull final CompletionLocation location) {
    return forLocation(location).apply(element);
  }
}