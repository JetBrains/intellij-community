// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.util;

import com.intellij.codeInspection.reference.RefEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public final class RefEntityAlphabeticalComparator implements Comparator<RefEntity> {

  @Override
  public int compare(final @NotNull RefEntity o1, final @NotNull RefEntity o2) {
    if (o1 == o2) return 0;
    return o1.getQualifiedName().compareToIgnoreCase(o2.getQualifiedName());
  }

  private static final class RefEntityAlphabeticalComparatorHolder {
    private static final RefEntityAlphabeticalComparator ourEntity = new RefEntityAlphabeticalComparator();
  }

  public static RefEntityAlphabeticalComparator getInstance() {

    return RefEntityAlphabeticalComparatorHolder.ourEntity;
  }
}
