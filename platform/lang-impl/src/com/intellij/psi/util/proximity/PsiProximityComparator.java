// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.util.proximity;

import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.Weigher;
import com.intellij.psi.WeighingComparable;
import com.intellij.psi.WeighingService;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;

public final class PsiProximityComparator implements Comparator<Object> {
  public static final Key<ProximityStatistician> STATISTICS_KEY = Key.create("proximity");
  public static final Key<ProximityWeigher> WEIGHER_KEY = Key.create("proximity");
  private static final Key<Module> MODULE_BY_LOCATION = Key.create("ModuleByLocation");
  private final PsiElement myContext;

  private final Map<PsiElement, WeighingComparable<PsiElement, ProximityLocation>> myProximities;

  private final Module myContextModule;

  public PsiProximityComparator(@Nullable PsiElement context) {
    myContext = context;
    myContextModule = context == null ? null : ModuleUtilCore.findModuleForPsiElement(context);
    myProximities = FactoryMap.create(key -> getProximity(key, myContext));
  }

  @Override
  public int compare(final Object o1, final Object o2) {
    PsiElement element1 = getPsiElement(o1);
    PsiElement element2 = getPsiElement(o2);
    if (element1 == null) return element2 == null ? 0 : 1;
    if (element2 == null) return -1;

    if (myContext != null && myContextModule != null) {
      final ProximityLocation location = new ProximityLocation(myContext, myContextModule);
      StatisticsInfo info1 = StatisticsManager.serialize(STATISTICS_KEY, element1, location);
      StatisticsInfo info2 = StatisticsManager.serialize(STATISTICS_KEY, element2, location);
      if (info1 != null && info2 != null) {
        StatisticsManager statisticsManager = StatisticsManager.getInstance();
        int count1 = statisticsManager.getLastUseRecency(info1);
        int count2 = statisticsManager.getLastUseRecency(info2);
        if (count1 != count2) {
          return count1 < count2 ? -1 : 1;
        }
      }
    }

    final WeighingComparable<PsiElement, ProximityLocation> proximity1 = myProximities.get(element1);
    final WeighingComparable<PsiElement, ProximityLocation> proximity2 = myProximities.get(element2);
    if (proximity1 == null || proximity2 == null) {
      return 0;
    }
    return -proximity1.compareTo(proximity2);
  }

  private static PsiElement getPsiElement(Object o) {
    return o instanceof PsiElement ? (PsiElement)o :
           o instanceof PsiElementNavigationItem ? ((PsiElementNavigationItem)o).getTargetElement() : null;
  }


  public static @Nullable WeighingComparable<PsiElement, ProximityLocation> getProximity(final PsiElement element, final PsiElement context) {
    if (element == null) return null;
    final Module contextModule = context != null ? ModuleUtilCore.findModuleForPsiElement(context) : null;
    return WeighingService.weigh(WEIGHER_KEY, element, new ProximityLocation(context, contextModule));
  }

  public static @Nullable WeighingComparable<PsiElement, ProximityLocation> getProximity(final Computable<? extends PsiElement> elementComputable, final PsiElement context, ProcessingContext processingContext) {
    PsiElement element = elementComputable.compute();
    if (element == null || context == null) return null;
    Module contextModule = processingContext.get(MODULE_BY_LOCATION);
    if (contextModule == null) {
      contextModule = ModuleUtilCore.findModuleForPsiElement(context);
      processingContext.put(MODULE_BY_LOCATION, contextModule);
    }

    return new WeighingComparable<>(elementComputable,
                                    new ProximityLocation(context, contextModule, processingContext),
                                    WeighingService.getWeighers(WEIGHER_KEY).toArray(new Weigher[0]));
  }
}