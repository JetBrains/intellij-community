// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.intentions.openInProject;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class GradleProjectRootFinder extends ProjectRootFinder {
  @Override
  protected boolean isProjectDir(@NotNull VirtualFile file) {
    return containsChild(file,
                         child -> {
                           if (!child.isDirectory()) {
                             String name = child.getName();
                             if ("settings.gradle".equals(name) ||
                                 "settings.gradle.kts".equals(name) ||
                                 "build.gradle".equals(name) ||
                                 "build.gradle.kts".equals(name)) {
                               return true;
                             }
                           }
                           return false;
                         });
  }

  @Override
  protected boolean requiresConfirmation() {
    return true;
  }
}
