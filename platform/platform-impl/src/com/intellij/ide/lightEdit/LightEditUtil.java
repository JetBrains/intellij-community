// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class LightEditUtil {

  private LightEditUtil() {
  }

  private static final String ENABLED_FILE_OPEN_KEY = "light.edit.file.open.enabled";

  public static boolean openFile(@NotNull VirtualFile file) {
    if (Registry.is(ENABLED_FILE_OPEN_KEY)) {
      LightEditService.getInstance().openFile(file);
      return true;
    }
    return false;
  }

  public static boolean openFile(@NotNull Path path) {
    VirtualFile virtualFile = VfsUtil.findFile(path, true);
    if (virtualFile != null) {
      return openFile(virtualFile);
    }
    return false;
  }

  public static Project getProject() {
    return ProjectManager.getInstance().getDefaultProject();
  }

}
