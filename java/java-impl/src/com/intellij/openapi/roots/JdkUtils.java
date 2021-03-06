// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JdkUtils {
  @Nullable
  public static Sdk getJdkForElement(@NotNull PsiElement element) {
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    if (virtualFile == null) return null;
    final List<OrderEntry> entries = ProjectRootManager.getInstance(element.getProject()).getFileIndex().getOrderEntriesForFile(virtualFile);
    Sdk jdk = null;
    for (OrderEntry orderEntry : entries) {
      if (orderEntry instanceof JdkOrderEntry) {
        jdk = ((JdkOrderEntry)orderEntry).getJdk();
        if (jdk != null) break;
      }
    }
    return jdk;
  }
}
