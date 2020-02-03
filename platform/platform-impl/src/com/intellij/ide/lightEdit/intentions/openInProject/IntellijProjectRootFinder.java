// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.intentions.openInProject;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

class IntellijProjectRootFinder extends ProjectRootFinder {

  @Override
  protected boolean isProjectDir(@NotNull VirtualFile file) {
    if (file.isDirectory()) {
      for (VirtualFile child : file.getChildren()) {
        if (PathMacroUtil.DIRECTORY_STORE_NAME.equals(child.getName())) {
          return true;
        }
      }
    }
    return false;
  }
}
