// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.customRegions.CustomRegionTreeElement;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class VisibilityComparator implements Comparator {
  private static final Logger LOG = Logger.getInstance(VisibilityComparator.class);
  private static final int GROUP_ACCESS_SUBLEVEL = 1;
  public static final Comparator INSTANCE = new VisibilityComparator(null);

  private final Comparator myNextComparator;
  private static final int UNKNOWN_ACCESS_LEVEL = -1;

  public VisibilityComparator(Comparator comparator) {
    myNextComparator = comparator;
  }

  @Override
  public int compare(@NotNull Object descriptor1, @NotNull Object descriptor2) {
    int accessLevel1 = getAccessLevel(descriptor1);
    int accessLevel2 = getAccessLevel(descriptor2);
    if (accessLevel1 == accessLevel2 && myNextComparator != null) {
      return myNextComparator.compare(descriptor1, descriptor2);
    }
    return accessLevel2 - accessLevel1;
  }

  private static int getAccessLevel(@NotNull Object element) {
    if (element instanceof AccessLevelProvider) {
      return ((AccessLevelProvider)element).getAccessLevel() * (GROUP_ACCESS_SUBLEVEL + 1) + ((AccessLevelProvider)element).getSubLevel();
    }
    else {
      if (!(element instanceof CustomRegionTreeElement)) {
        LOG.error(element.getClass().getName());
      }
      return UNKNOWN_ACCESS_LEVEL;
    }
  }
}
