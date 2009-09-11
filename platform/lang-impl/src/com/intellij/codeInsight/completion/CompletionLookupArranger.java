package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupArranger;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.lookup.impl.LookupItemWeightComparable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.WeighingService;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CompletionLookupArranger extends LookupArranger {
  public static final Key<LookupItemWeightComparable> RELEVANCE_KEY = Key.create("RELEVANCE_KEY");
  @NonNls public static final String SELECTED = "selected";
  @NonNls public static final String IGNORED = "ignored";
  private final CompletionLocation myLocation;
  public static final Key<Comparable[]> WEIGHT = Key.create("WEIGHT");

  public CompletionLookupArranger(final CompletionParameters parameters) {
    myLocation = new CompletionLocation(parameters);
  }

  @Override
  public void sortItems(List<LookupElement> items) {
    final PsiProximityComparator proximityComparator = new PsiProximityComparator(myLocation.getCompletionParameters().getPosition());
    Collections.sort(items, new Comparator<LookupElement>() {
      public int compare(LookupElement o1, LookupElement o2) {
        LookupElement c1 = getCoreElement(o1);
        LookupElement c2 = getCoreElement(o2);

        if (c1 instanceof LookupItem && c2 instanceof LookupItem) {
          double priority1 = ((LookupItem)c1).getPriority();
          double priority2 = ((LookupItem)c2).getPriority();
          if (priority1 > priority2) return -1;
          if (priority2 > priority1) return 1;
        }

        int grouping1 = c1.getGrouping();
        int grouping2 = c2.getGrouping();
        if (grouping1 > grouping2) return -1;
        if (grouping2 > grouping1) return 1;

        int stringCompare = o1.getLookupString().compareToIgnoreCase(o2.getLookupString());
        if (stringCompare != 0) return stringCompare;

        return proximityComparator.compare(o1.getObject(), o2.getObject());
      }
    });
  }

  private static LookupElement getCoreElement(LookupElement element) {
    while (element instanceof LookupElementDecorator) {
      element = ((LookupElementDecorator) element).getDelegate();
    }
    return element;
  }


  public void itemSelected(LookupElement item, final Lookup lookup) {
    final StatisticsManager manager = StatisticsManager.getInstance();
    manager.incUseCount(CompletionService.STATISTICS_KEY, item, myLocation);
    final List<LookupElement> items = lookup.getItems();
    final LookupImpl lookupImpl = (LookupImpl)lookup;
    final int count = Math.min(lookupImpl.getPreferredItemsCount(), lookupImpl.getList().getSelectedIndex());
    for (int i = 0; i < count; i++) {
      final LookupElement element = items.get(i);
      StatisticsInfo info = StatisticsManager.serialize(CompletionService.STATISTICS_KEY, element, myLocation);
      if (info != null && info != StatisticsInfo.EMPTY && manager.getUseCount(info) == 0) {
        manager.incUseCount(new StatisticsInfo(composeContextWithValue(info), item == element ? SELECTED : IGNORED));
      }
    }

  }

  public int suggestPreselectedItem(List<LookupElement> sorted) {
    final CompletionPreselectSkipper[] skippers = CompletionPreselectSkipper.EP_NAME.getExtensions();

    nextItem: for (int i = 0; i < sorted.size(); i++){
      LookupElement item = sorted.get(i);
      final Object obj = item.getObject();
      if (obj instanceof PsiElement && !((PsiElement)obj).isValid()) continue;

      for (final CompletionPreselectSkipper skipper : skippers) {
        if (skipper.skipElement(item, myLocation)) {
          continue nextItem;
        }
      }

      return i;
    }
    return sorted.size() - 1;
  }

  public static String composeContextWithValue(final StatisticsInfo info) {
    return info.getContext() + "###" + info.getValue();
  }

  public Comparable[] getWeight(final LookupElement item) {
    if (item.getUserData(WEIGHT) != null) return item.getUserData(WEIGHT);

    final Comparable[] result = new Comparable[]{WeighingService.weigh(CompletionService.WEIGHER_KEY, item, myLocation)};

    item.putUserData(WEIGHT, result);

    return result;
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
        //noinspection unchecked
        final int res = w1.compareTo(w2);
        if (res != 0) return -res;
      }
    }

    return 0;
  }

  @Override
  public LookupItemWeightComparable getRelevance(LookupElement item) {
    if (item.getUserData(RELEVANCE_KEY) != null) return item.getUserData(RELEVANCE_KEY);

    final double priority = item instanceof LookupItem ? ((LookupItem)item).getPriority() : 0;
    final LookupItemWeightComparable result = new LookupItemWeightComparable(priority, getWeight(item));

    item.putUserData(RELEVANCE_KEY, result);

    return result;
  }

}
