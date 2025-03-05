// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.SorterUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * @author Konstantin Bulenkov
 */
public class AnonymousClassesSorter implements Sorter {
  public static Sorter INSTANCE = new AnonymousClassesSorter();

  private final Comparator myComparator = (o1, o2) -> {
    String s1 = SorterUtil.getStringPresentation(o1);
    String s2 = SorterUtil.getStringPresentation(o2);
    if (s1.startsWith("$") && s2.startsWith("$")) {
      try {
        return Integer.parseInt(s1.substring(1)) - Integer.parseInt(s2.substring(1));
      } catch (NumberFormatException e) {//
      }
    }
    return 0;
  };
  
  @Override
  public @NotNull Comparator getComparator() {
    return myComparator;
  }

  @Override
  public boolean isVisible() {
    return false;
  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    return ALPHA_SORTER.getPresentation();
  }

  @Override
  public @NotNull String getName() {
    return "ANONYMOUS_CLASSES_SORTER";
  }
}
