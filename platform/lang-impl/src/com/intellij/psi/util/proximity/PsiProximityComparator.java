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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 13, 2007
 * Time: 2:09:28 PM
 */
package com.intellij.psi.util.proximity;

import com.intellij.extapi.psi.MetadataPsiElementBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.WeighingComparable;
import com.intellij.psi.WeighingService;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public class PsiProximityComparator implements Comparator<Object> {
  public static final Key<ProximityStatistician> STATISTICS_KEY = Key.create("proximity");
  public static final Key<ProximityWeigher> WEIGHER_KEY = Key.create("proximity");
  private final PsiElement myContext;
  private final FactoryMap<PsiElement, WeighingComparable<PsiElement, ProximityLocation>> myProximities = new FactoryMap<PsiElement, WeighingComparable<PsiElement, ProximityLocation>>() {
    @Override
    protected WeighingComparable<PsiElement, ProximityLocation> create(final PsiElement key) {
      return getProximity(key, myContext);
    }
  };
  private static final Key<Module> MODULE_BY_LOCATION = Key.create("ModuleByLocation");

  public PsiProximityComparator(PsiElement context) {
    myContext = context;
  }

  public int compare(final Object o1, final Object o2) {
    PsiElement element1 = o1 instanceof PsiElement ? (PsiElement)o1 : null;
    PsiElement element2 = o2 instanceof PsiElement ? (PsiElement)o2 : null;
    if (element1 == null) return element2 == null ? 0 : 1;
    if (element2 == null) return -1;

    final WeighingComparable<PsiElement, ProximityLocation> proximity1 = myProximities.get(element1);
    final WeighingComparable<PsiElement, ProximityLocation> proximity2 = myProximities.get(element2);
    if (proximity1 == null || proximity2 == null) {
      return 0;
    }
    if (!proximity1.equals(proximity2)) {
      return - proximity1.compareTo(proximity2);
    }

    Module contextModule = ModuleUtil.findModuleForPsiElement(myContext);
    if (contextModule == null) return 0;

    StatisticsManager statisticsManager = StatisticsManager.getInstance();
    final ProximityLocation location = new ProximityLocation(myContext, contextModule);
    int count1 = statisticsManager.getUseCount(STATISTICS_KEY, element1, location);
    int count2 = statisticsManager.getUseCount(STATISTICS_KEY, element1, location);
    return count2 - count1;
  }


  @Nullable
  public static WeighingComparable<PsiElement, ProximityLocation> getProximity(final PsiElement element, final PsiElement context) {
    if (element == null) return null;
    if (element instanceof MetadataPsiElementBase) return null;
    if (context == null) return null;
    Module contextModule = ModuleUtil.findModuleForPsiElement(context);
    if (contextModule == null) return null;

    return WeighingService.weigh(WEIGHER_KEY, element, new ProximityLocation(context, contextModule));
  }

  @Nullable
  public static WeighingComparable<PsiElement, ProximityLocation> getProximity(final PsiElement element, final PsiElement context, ProcessingContext processingContext) {
    if (element == null) return null;
    if (element instanceof MetadataPsiElementBase) return null;
    if (context == null) return null;
    Module contextModule = processingContext.get(MODULE_BY_LOCATION);
    if (contextModule == null) {
      contextModule = ModuleUtil.findModuleForPsiElement(context);
      processingContext.put(MODULE_BY_LOCATION, contextModule);
    }

    if (contextModule == null) return null;

    return WeighingService.weigh(WEIGHER_KEY, element, new ProximityLocation(context, contextModule, processingContext));
  }

}