// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.intentions.openInProject;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

final class GitProjectRootFinder extends ProjectRootFinder {
  @Override
  protected boolean isProjectDir(@NotNull VirtualFile file) {
    return containsChild(file, virtualFile -> virtualFile.isDirectory() && ".git".equals(virtualFile.getName()));
  }

  @Override
  protected boolean requiresConfirmation() {
    return true;
  }
}
