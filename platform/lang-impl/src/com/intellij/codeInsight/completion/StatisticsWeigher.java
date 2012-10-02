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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
*/
public class StatisticsWeigher extends CompletionWeigher {
  private static final StatisticsManager ourStatManager = StatisticsManager.getInstance();
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.StatisticsWeigher.LookupStatisticsWeigher");
  private static final Key<StatisticsInfo> BASE_STATISTICS_INFO = Key.create("Base statistics info");

  @Override
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
      final StatisticsInfo baseInfo = getBaseStatisticsInfo(item, myLocation);
      if (baseInfo == StatisticsInfo.EMPTY) {
        return 0;
      }
      int maxUseCount = 0;
      int minRecency = Integer.MAX_VALUE;
      for (StatisticsInfo eachInfo : composeStatsWithPrefix(baseInfo, myLocation.getCompletionParameters().getLookup().itemPattern(item))) {
        maxUseCount = Math.max(maxUseCount, ourStatManager.getUseCount(eachInfo));
        minRecency = Math.min(minRecency, ourStatManager.getLastUseRecency(eachInfo));
      }
      return minRecency == Integer.MAX_VALUE ? maxUseCount : 100 - minRecency;
    }

  }

  public static void clearBaseStatisticsInfo(LookupElement item) {
    item.putUserData(BASE_STATISTICS_INFO, null);
  }

  @NotNull
  public static StatisticsInfo getBaseStatisticsInfo(LookupElement item, @Nullable CompletionLocation location) {
    StatisticsInfo info = BASE_STATISTICS_INFO.get(item);
    if (info == null) {
      if (location == null) {
        return StatisticsInfo.EMPTY;
      }
      BASE_STATISTICS_INFO.set(item, info = calcBaseInfo(item, location));
    }
    return info;
  }

  @NotNull
  private static StatisticsInfo calcBaseInfo(LookupElement item, @NotNull CompletionLocation location) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread());
    }
    StatisticsInfo info = StatisticsManager.serialize(CompletionService.STATISTICS_KEY, item, location);
    return info == null ? StatisticsInfo.EMPTY : info;
  }

  public static List<StatisticsInfo> composeStatsWithPrefix(StatisticsInfo info, final String fullPrefix) {
    ArrayList<StatisticsInfo> infos = new ArrayList<StatisticsInfo>(fullPrefix.length() + 1);
    infos.add(info);
    for (int i = 1; i <= fullPrefix.length(); i++) {
      String subPrefix = fullPrefix.substring(0, i);
      infos.add(new StatisticsInfo(info.getContext() + "###prefix=" + subPrefix, info.getValue()));
    }
    return infos;
  }
}
