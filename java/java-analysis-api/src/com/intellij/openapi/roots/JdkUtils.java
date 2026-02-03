// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.jps.entities.SdkEntity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JdkUtils {
  public static @Nullable Sdk getJdkForElement(@NotNull PsiElement element) {
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    if (virtualFile == null) return null;
    var jdks = ProjectRootManager.getInstance(element.getProject()).getFileIndex().findContainingSdks(virtualFile);
    for (SdkEntity jdk : jdks) {
      var sdkBridge = ProjectJdkTable.getInstance(element.getProject()).findJdk(jdk.getName());
      if (sdkBridge != null) {
        return sdkBridge;
      }
    }
    return null;
  }
}
