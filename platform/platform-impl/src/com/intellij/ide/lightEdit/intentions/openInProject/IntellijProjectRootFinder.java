// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.intentions.openInProject;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

final class IntellijProjectRootFinder extends ProjectRootFinder {

  @Override
  protected boolean isProjectDir(@NotNull VirtualFile file) {
    return containsChild(file,
                         child -> child.isDirectory() && PathMacroUtil.DIRECTORY_STORE_NAME.equals(child.getName()));
  }

  @Override
  protected boolean requiresConfirmation() {
    return false;
  }
}
