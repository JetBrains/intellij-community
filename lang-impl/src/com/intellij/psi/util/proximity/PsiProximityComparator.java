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
import com.intellij.psi.WeigherKey;
import com.intellij.psi.WeighingComparable;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public class PsiProximityComparator implements Comparator<Object> {
  public static final WeigherKey<PsiElement, ProximityLocation> KEY = WeigherKey.create("proximity");
  public static final Key<ProximityStatistician> STATISTICS_KEY = Key.create("proximity");
  private final PsiElement myContext;

  public PsiProximityComparator(PsiElement context) {
    myContext = context;
  }

  public int compare(final Object o1, final Object o2) {
    PsiElement element1 = o1 instanceof PsiElement ? (PsiElement)o1 : null;
    PsiElement element2 = o2 instanceof PsiElement ? (PsiElement)o2 : null;
    if (element1 == null) return element2 == null ? 0 : 1;
    if (element2 == null) return -1;

    final WeighingComparable<PsiElement, ProximityLocation> proximity1 = getProximity(element1, myContext);
    final WeighingComparable<PsiElement, ProximityLocation> proximity2 = getProximity(element2, myContext);
    if (proximity1 == null || proximity2 == null) {
      return 0;
    }
    if (!proximity1.equals(proximity2)) {
      return proximity1.compareTo(proximity2);
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

    return KEY.weigh(element, new ProximityLocation(context, contextModule));
  }

}