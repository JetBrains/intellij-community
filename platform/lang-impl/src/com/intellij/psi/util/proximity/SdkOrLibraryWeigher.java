// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util.proximity;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class SdkOrLibraryWeigher extends ProximityWeigher {

  @Override
  public Comparable weigh(final @NotNull PsiElement element, final @NotNull ProximityLocation location) {
    Project project = location.getProject();
    return project == null ? null : isJdkElement(element, project);
  }

  private static boolean isJdkElement(PsiElement element, final @NotNull Project project) {
    final VirtualFile file = PsiUtilCore.getVirtualFile(element);
    if (file != null) {
      List<OrderEntry> orderEntries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(file);
      if (!orderEntries.isEmpty() && orderEntries.get(0) instanceof JdkOrderEntry) {
        return true;
      }
    }
    return false;
  }
}
