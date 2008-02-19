package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiType;
import com.intellij.psi.statistics.StatisticsManager;

public class CompletionPreferencePolicy implements LookupItemPreferencePolicy{
  private String myPrefix;
  private final CompletionParameters myParameters;
  private final CompletionType myCompletionType;

  public CompletionPreferencePolicy(String prefix, final CompletionParameters parameters, final CompletionType completionType) {
    myParameters = parameters;
    myCompletionType = completionType;
    setPrefix(prefix);
  }

  public CompletionType getCompletionType() {
    return myCompletionType;
  }

  public void setPrefix(String prefix) {
    myPrefix = prefix;
  }

  public void itemSelected(LookupItem item) {
    final Object o = item.getObject();
    if (o instanceof PsiMember){
      final PsiType qualifierType = JavaCompletionUtil.getQualifierType(item);
      if (qualifierType != null){
        StatisticsManager.getInstance().incMemberUseCount(qualifierType, (PsiMember)o);
      }
    }
  }

  public Comparable[] getWeight(final LookupItem<?> item) {
    if (item.getAttribute(LookupItem.WEIGHT) != null) return item.getAttribute(LookupItem.WEIGHT);

    final Comparable[] result = new Comparable[]{CompletionRegistrar.WEIGHING_KEY.weigh(item, new CompletionWeighingLocation(myCompletionType, myPrefix, myParameters))};

    item.setAttribute(LookupItem.WEIGHT, result);

    return result;
  }


  public int compare(final LookupItem item1, final LookupItem item2) {
    if (item1 == item2) return 0;

    if (myCompletionType == CompletionType.SMART) {
      if (item2.getAttribute(LookupItem.DONT_PREFER) != null) return -1;
      return 0;
    }

    if (item1.getAllLookupStrings().contains(myPrefix)) return -1;
    if (item2.getAllLookupStrings().contains(myPrefix)) return 1;

    return doCompare(item1.getPriority(), item2.getPriority(), getWeight(item1), getWeight(item2));
  }

  public static int doCompare(final double priority1, final double priority2, final Comparable[] weight1, final Comparable[] weight2) {
    if (priority1 != priority2) {
      final double v = priority1 - priority2;
      if (v > 0) return -1;
      if (v < 0) return 1;
    }

    for (int i = 0; i < weight1.length; i++) {
      final Comparable w1 = weight1[i];
      final Comparable w2 = weight2[i];
      if (w1 != null || w2 != null) {
        if (w1 == null) return 1;
        if (w2 == null) return -1;
        final int res = w1.compareTo(w2);
        if (res != 0) return -res;
      }
    }

    return 0;
  }

}