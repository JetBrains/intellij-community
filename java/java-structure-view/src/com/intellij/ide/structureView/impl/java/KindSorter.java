// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class KindSorter implements Sorter {
  public static final Sorter INSTANCE = new KindSorter(false);
  public static final Sorter POPUP_INSTANCE = new KindSorter(true);

  public KindSorter(boolean isPopup) {
    this.isPopup = isPopup;
  }

  public static final @NonNls String ID = "KIND";
  private final boolean isPopup;

  private final Comparator COMPARATOR = new Comparator() {
    @Override
    public int compare(final Object o1, final Object o2) {
      return getWeight(o1) - getWeight(o2);
    }

    private int getWeight(final Object value) {
      if (value instanceof JavaAnonymousClassTreeElement) {
        return 55;
      }
      if (value instanceof JavaClassTreeElement) {
        return isPopup ? 53 : 10;
      }
      if (value instanceof ClassInitializerTreeElement) {
        return 15;
      }
      if (value instanceof SuperTypeGroup) {
        return 20;
      }
      if (value instanceof PsiMethodTreeElement methodTreeElement) {
        final PsiMethod method = methodTreeElement.getMethod();

        return method != null && method.isConstructor() ? 30 : 35;
      }
      if (value instanceof PropertyGroup) {
        return 40;
      }
      if (value instanceof PsiFieldTreeElement) {
        return 50;
      }
      return 60;
    }
  };

  @Override
  public @NotNull Comparator getComparator() {
    return COMPARATOR;
  }

  @Override
  public boolean isVisible() {
    return false;
  }

  @Override
  public @NotNull ActionPresentation getPresentation() {
    throw new IllegalStateException();
  }

  @Override
  public @NotNull String getName() {
    return ID;
  }
}
