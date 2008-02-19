
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiType;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.containers.HashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class CompletionPreferencePolicy implements LookupItemPreferencePolicy{
  private final ExpectedTypeInfo[] myExpectedInfos;

  private final TObjectIntHashMap<LookupItem> myItemToIndexMap = new TObjectIntHashMap<LookupItem>();

  private String myPrefix;
  private final CompletionParameters myParameters;
  private final CompletionType myCompletionType;

  public CompletionPreferencePolicy(PsiManager manager, LookupItem[] allItems, ExpectedTypeInfo[] expectedInfos, String prefix, @NotNull PsiElement position,
                                    final CompletionParameters parameters,
                                    final CompletionType completionType) {
    myParameters = parameters;
    myCompletionType = completionType;
    setPrefix( prefix );
    if(expectedInfos != null){
      final Map<PsiType, ExpectedTypeInfo> map = new HashMap<PsiType, ExpectedTypeInfo>(expectedInfos.length);
      for (final ExpectedTypeInfo expectedInfo : expectedInfos) {
        if (!map.containsKey(expectedInfo.getType())) {
          map.put(expectedInfo.getType(), expectedInfo);
        }
      }
      myExpectedInfos = map.values().toArray(new ExpectedTypeInfo[map.size()]);
    }
    else myExpectedInfos = null;
    synchronized(myItemToIndexMap){
      for(int i = 0; i < allItems.length; i++){
        myItemToIndexMap.put(allItems[i], i + 1);
      }
    }
  }

  public void setPrefix(String prefix) {
    myPrefix = prefix;
  }

  @Nullable
  public ExpectedTypeInfo[] getExpectedInfos() {
    return myExpectedInfos;
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

    if (LookupManagerImpl.isUseNewSorting()) {
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