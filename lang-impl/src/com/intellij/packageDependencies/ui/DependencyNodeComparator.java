package com.intellij.packageDependencies.ui;

import java.util.Comparator;

public class DependencyNodeComparator implements Comparator<PackageDependenciesNode>{
  private final boolean mySortByType;

  public DependencyNodeComparator(final boolean sortByType) {
    mySortByType = sortByType;
  }

  public DependencyNodeComparator() {
    mySortByType = false;
  }

  public int compare(PackageDependenciesNode p1, PackageDependenciesNode p2) {
    if (p1.getWeight() != p2.getWeight()) return p1.getWeight() - p2.getWeight();
    if (mySortByType) {
      if (p1 instanceof Comparable) {
        return ((Comparable)p1).compareTo(p2);
      }
    }
    return p1.toString().compareTo(p2.toString());
  }
}