// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.intentions.openInProject;

import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ProjectRootSearchUtil {
  private final static ProjectRootFinder[] ROOT_FINDERS = {
    new IntellijProjectRootFinder()
  };

  private ProjectRootSearchUtil() {
  }

  static @Nullable VirtualFile findProjectRoot(@NotNull VirtualFile sourceFile) {
    Ref<VirtualFile> result = Ref.create();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> {
        for (ProjectRootFinder finder : ROOT_FINDERS) {
          VirtualFile root = finder.findProjectRoot(sourceFile);
          if (root != null) {
            result.set(root);
            break;
          }
        }
      },
      "Searching project root",
      true,
      LightEditUtil.getProject()
    );
    return result.get();
  }
}
