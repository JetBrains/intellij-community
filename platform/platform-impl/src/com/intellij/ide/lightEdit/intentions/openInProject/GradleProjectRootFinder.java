// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.intentions.openInProject;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class GradleProjectRootFinder extends ProjectRootFinder {
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
