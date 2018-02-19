// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy;

import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.SourceComparator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * @author yole
 */
public class JavaHierarchyUtil {
  private JavaHierarchyUtil() { }

  @Nullable
  public static String getPackageName(@NotNull PsiClass psiClass) {
    return PsiUtil.getPackageName(psiClass);
  }

  public static Comparator<NodeDescriptor> getComparator(Project project) {
    HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(project).getState();
    return state != null && state.SORT_ALPHABETICALLY ? AlphaComparator.INSTANCE : SourceComparator.INSTANCE;
  }
}