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
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
*/
public class StatisticsWeigher extends CompletionWeigher {
  private static final StatisticsManager ourStatManager = StatisticsManager.getInstance();

  public Comparable weigh(@NotNull final LookupElement item, @NotNull final CompletionLocation location) {
    throw new UnsupportedOperationException();
  }

  public static class LookupStatisticsWeigher extends LookupElementWeigher {
    private final CompletionLocation myLocation;

    public LookupStatisticsWeigher(CompletionLocation location) {
      super("stats", true, true);
      myLocation = location;
    }

    @Override
    public Integer weigh(@NotNull LookupElement item) {
      final StatisticsInfo info = StatisticsManager.serialize(CompletionService.STATISTICS_KEY, item, myLocation);
      if (info == null || info == StatisticsInfo.EMPTY) {
        return 0;
      }
      int max = 0;
      for (StatisticsInfo statisticsInfo : composeStatsWithPrefix(info, myLocation, item)) {
        max = Math.max(max, ourStatManager.getUseCount(statisticsInfo));
      }
      return max;
    }
  }

  public static List<StatisticsInfo> composeStatsWithPrefix(StatisticsInfo info, CompletionLocation location, LookupElement item) {
    String fullPrefix = location.getCompletionParameters().getLookup().itemPattern(item);
    ArrayList<StatisticsInfo> infos = new ArrayList<StatisticsInfo>(fullPrefix.length() + 1);
    infos.add(info);
    for (int i = 1; i <= fullPrefix.length(); i++) {
      String subPrefix = fullPrefix.substring(0, i);
      infos.add(new StatisticsInfo(info.getContext() + "###prefix=" + subPrefix, info.getValue()));
    }
    return infos;
  }
}
