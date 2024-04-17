// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy;

import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.SourceComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;


public final class JavaHierarchyUtil {
  private JavaHierarchyUtil() { }

  public static @Nullable @NlsSafe String getPackageName(@NotNull PsiClass psiClass) {
    return PsiUtil.getPackageName(psiClass);
  }

  public static @NotNull Comparator<NodeDescriptor<?>> getComparator(@NotNull Project project) {
    HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(project).getState();
    return state != null && state.SORT_ALPHABETICALLY ? AlphaComparator.INSTANCE : SourceComparator.INSTANCE;
  }
}