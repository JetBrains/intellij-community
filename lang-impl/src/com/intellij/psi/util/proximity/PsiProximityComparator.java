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
import com.intellij.psi.PsiElement;
import com.intellij.psi.WeighingComparable;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.ProximityWeigherExtension;
import com.intellij.psi.util.ProximityWeighingLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public class PsiProximityComparator implements Comparator<Object> {
  private final PsiElement myContext;

  public PsiProximityComparator(PsiElement context) {
    myContext = context;
  }

  public int compare(final Object o1, final Object o2) {
    PsiElement element1 = o1 instanceof PsiElement ? (PsiElement)o1 : null;
    PsiElement element2 = o2 instanceof PsiElement ? (PsiElement)o2 : null;
    if (element1 == null) return element2 == null ? 0 : 1;
    if (element2 == null) return -1;

    final WeighingComparable<PsiElement,ProximityWeighingLocation> proximity1 = getProximity(element1, getContext());
    final WeighingComparable<PsiElement,ProximityWeighingLocation> proximity2 = getProximity(element2, getContext());
    if (proximity1 == null || proximity2 == null) {
      return 0;
    }
    if (!proximity1.equals(proximity2)) {
      return proximity1.compareTo(proximity2);
    }
    StatisticsManager statisticsManager = StatisticsManager.getInstance();
    int count1 = statisticsManager.getMemberUseCount(element1);
    int count2 = statisticsManager.getMemberUseCount(element2);
    return count2 - count1;
  }

  public PsiElement getContext() {
    return myContext;
  }

  @Nullable
  public static WeighingComparable<PsiElement, ProximityWeighingLocation> getProximity(final PsiElement element, final PsiElement context) {
    if (element == null) return null;
    if (element instanceof MetadataPsiElementBase) return null;
    if (context == null) return null;
    Module contextModule = ModuleUtil.findModuleForPsiElement(context);
    if (contextModule == null) return null;

    return ProximityWeigherExtension.KEY.weigh(element, new ProximityWeighingLocation(context, contextModule));
  }

}