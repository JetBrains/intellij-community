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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
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
    private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.StatisticsWeigher.LookupStatisticsWeigher");
    private static final Key<StatisticsInfo> BASE_INFO = Key.create("Base statistics info");
    private final CompletionLocation myLocation;

    public LookupStatisticsWeigher(CompletionLocation location) {
      super("stats", true, true);
      myLocation = location;
    }

    @Override
    public Integer weigh(@NotNull LookupElement item) {
      final StatisticsInfo info = getBaseStatisticsInfo(item);
      if (info == StatisticsInfo.EMPTY) {
        return 0;
      }
      int max = 0;
      for (StatisticsInfo statisticsInfo : composeStatsWithPrefix(info, myLocation, item)) {
        max = Math.max(max, ourStatManager.getUseCount(statisticsInfo));
      }
      return max;
    }

    @NotNull
    private StatisticsInfo getBaseStatisticsInfo(LookupElement item) {
      StatisticsInfo info = BASE_INFO.get(item);
      if (info == null) {
        BASE_INFO.set(item, info = calcBaseInfo(item));
      }
      return info;
    }

    @NotNull
    private StatisticsInfo calcBaseInfo(LookupElement item) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread());
      }
      StatisticsInfo info = StatisticsManager.serialize(CompletionService.STATISTICS_KEY, item, myLocation);
      return info == null ? StatisticsInfo.EMPTY : info;
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
