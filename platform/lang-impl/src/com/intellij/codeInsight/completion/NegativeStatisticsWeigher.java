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
import com.intellij.psi.statistics.StatisticsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class NegativeStatisticsWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, @Nullable final CompletionLocation location) {
    if (location == null) {
      return null;
    }
    final StatisticsManager manager = StatisticsManager.getInstance();
    final StatisticsInfo info = StatisticsManager.serialize(CompletionService.STATISTICS_KEY, item, location);
    if (info == null || info == StatisticsInfo.EMPTY) return 0;

    final StatisticsInfo ignoreInfo = new StatisticsInfo(CompletionLookupArranger.composeContextWithValue(info), CompletionLookupArranger.IGNORED);
    final int count = manager.getUseCount(ignoreInfo);
    if (count >= StatisticsManager.OBLIVION_THRESHOLD - 1) {
      return -1;
    }

    return 0;
  }
}
