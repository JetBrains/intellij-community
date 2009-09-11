package com.intellij.codeInsight.lookup;

import java.util.List;

/**
 * @author peter
 */
public abstract class LookupArranger {
  public static final LookupArranger DEFAULT = new LookupArranger() {
    @Override
    public Comparable getRelevance(LookupElement element) {
      return 0;
    }

    @Override
    public void sortItems(List<LookupElement> items) {
    }
  };

  public abstract Comparable getRelevance(LookupElement element);

  public void itemSelected(LookupElement item, final Lookup lookup) {
  }

  public int suggestPreselectedItem(List<LookupElement> sorted) {
    return 0;
  }

  public abstract void sortItems(List<LookupElement> items);
}
