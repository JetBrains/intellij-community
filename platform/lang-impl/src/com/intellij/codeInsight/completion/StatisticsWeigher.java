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

import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FlatteningIterator;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
*/
public class StatisticsWeigher extends CompletionWeigher {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.StatisticsWeigher.LookupStatisticsWeigher");
  private static final Key<StatisticsInfo> BASE_STATISTICS_INFO = Key.create("Base statistics info");

  @Override
  public Comparable weigh(@NotNull final LookupElement item, @NotNull final CompletionLocation location) {
    throw new UnsupportedOperationException();
  }

  public static class LookupStatisticsWeigher extends Classifier<LookupElement> {
    private final CompletionLocation myLocation;
    private final Map<LookupElement, StatisticsComparable> myWeights = ContainerUtil.newIdentityHashMap();
    private final Set<LookupElement> myNoStats = ContainerUtil.newIdentityTroveSet();
    private int myPrefixChanges;

    public LookupStatisticsWeigher(CompletionLocation location, Classifier<LookupElement> next) {
      super(next, "stats");
      myLocation = location;
    }

    @Override
    public void addElement(@NotNull LookupElement element, @NotNull ProcessingContext context) {
      StatisticsInfo baseInfo = getBaseStatisticsInfo(element, myLocation);
      myWeights.put(element, new StatisticsComparable(weigh(baseInfo), baseInfo));
      if (baseInfo == StatisticsInfo.EMPTY) {
        myNoStats.add(element);
      }
      super.addElement(element, context);
    }

    private void checkPrefixChanged(ProcessingContext context) {
      int actualPrefixChanges = context.get(CompletionLookupArranger.PREFIX_CHANGES).intValue();
      if (myPrefixChanges != actualPrefixChanges) {
        myPrefixChanges = actualPrefixChanges;
        myWeights.clear();
      }
    }

    @NotNull
    @Override
    public Iterable<LookupElement> classify(@NotNull Iterable<LookupElement> source, @NotNull final ProcessingContext context) {
      checkPrefixChanged(context);

      final Collection<List<LookupElement>> byWeight = buildMapByWeight(source).descendingMap().values();

      List<LookupElement> initialList = getInitialNoStatElements(source, context);

      //noinspection unchecked
      final THashSet<LookupElement> initialSet = new THashSet<>(initialList, TObjectHashingStrategy.IDENTITY);
      final Condition<LookupElement> notInInitialList = element -> !initialSet.contains(element);

      return ContainerUtil.concat(initialList, new Iterable<LookupElement>() {
        @Override
        public Iterator<LookupElement> iterator() {
          return new FlatteningIterator<List<LookupElement>, LookupElement>(byWeight.iterator()) {
            @Override
            protected Iterator<LookupElement> createValueIterator(List<LookupElement> group) {
              return myNext.classify(ContainerUtil.findAll(group, notInInitialList), context).iterator();
            }
          };
        }
      });
    }

    private List<LookupElement> getInitialNoStatElements(Iterable<LookupElement> source, ProcessingContext context) {
      List<LookupElement> initialList = new ArrayList<>();
      for (LookupElement next : myNext.classify(source, context)) {
        if (myNoStats.contains(next)) {
          initialList.add(next);
        }
        else {
          break;
        }
      }
      return initialList;
    }

    private TreeMap<Integer, List<LookupElement>> buildMapByWeight(Iterable<LookupElement> source) {
      TreeMap<Integer, List<LookupElement>> map = new TreeMap<>();
      for (LookupElement element : source) {
        final int weight = getWeight(element).getScalar();
        List<LookupElement> list = map.get(weight);
        if (list == null) {
          map.put(weight, list = new SmartList<>());
        }
        list.add(element);
      }
      return map;
    }

    private StatisticsComparable getWeight(LookupElement t) {
      StatisticsComparable w = myWeights.get(t);
      if (w == null) {
        StatisticsInfo info = getBaseStatisticsInfo(t, myLocation);
        myWeights.put(t, w = new StatisticsComparable(weigh(info), info));
      }
      return w;
    }

    private static int weigh(final StatisticsInfo baseInfo) {
      if (baseInfo == StatisticsInfo.EMPTY) {
        return 0;
      }
      int minRecency = baseInfo.getLastUseRecency();
      return minRecency == Integer.MAX_VALUE ? 0 : StatisticsManager.RECENCY_OBLIVION_THRESHOLD - minRecency;
    }

    @NotNull
    @Override
    public List<Pair<LookupElement, Object>> getSortingWeights(@NotNull Iterable<LookupElement> items, @NotNull final ProcessingContext context) {
      checkPrefixChanged(context);
      return ContainerUtil.map(items, lookupElement -> new Pair<LookupElement, Object>(lookupElement, getWeight(lookupElement)));
    }

    @Override
    public void removeElement(@NotNull LookupElement element, @NotNull ProcessingContext context) {
      myWeights.remove(element);
      myNoStats.remove(element);
      super.removeElement(element, context);
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

}
