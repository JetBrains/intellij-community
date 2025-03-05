// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JdkUtils {
  public static @Nullable Sdk getJdkForElement(@NotNull PsiElement element) {
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    if (virtualFile == null) return null;
    final List<OrderEntry> entries = ProjectRootManager.getInstance(element.getProject()).getFileIndex().getOrderEntriesForFile(virtualFile);
    for (OrderEntry orderEntry : entries) {
      if (orderEntry instanceof JdkOrderEntry entry) {
        Sdk jdk = entry.getJdk();
        if (jdk != null) return jdk;
      }
    }
    return null;
  }
}
