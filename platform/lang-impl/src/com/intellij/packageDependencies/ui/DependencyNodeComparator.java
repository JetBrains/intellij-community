// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.packageDependencies.ui;

import java.util.Comparator;

public final class DependencyNodeComparator implements Comparator<PackageDependenciesNode>{
  private final boolean mySortByType;

  public DependencyNodeComparator(final boolean sortByType) {
    mySortByType = sortByType;
  }

  public DependencyNodeComparator() {
    mySortByType = false;
  }

  @Override
  public int compare(PackageDependenciesNode p1, PackageDependenciesNode p2) {
    if (p1.getWeight() != p2.getWeight()) return p1.getWeight() - p2.getWeight();
    if (mySortByType) {
      if (p1 instanceof Comparable) {
        return ((Comparable)p1).compareTo(p2);
      }
    }
    final String o1 = p1.toString();
    final String o2 = p2.toString();
    if (o1 == null) return o2 == null ? 0 : -1;
    if (o2 == null) return 1;
    return o1.compareToIgnoreCase(o2);
  }
}