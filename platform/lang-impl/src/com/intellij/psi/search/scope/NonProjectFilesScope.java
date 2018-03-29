// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.scope.packageSet.FilteredPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.Colored;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 * @author Sergey Malenkov
 */
@Colored(color = "ffffe4", darkVariant = "494539")
public final class NonProjectFilesScope extends NamedScope {
  public static final String NAME = IdeBundle.message("predefined.scope.non.project.files.name");
  public static final NonProjectFilesScope INSTANCE = new NonProjectFilesScope();

  private NonProjectFilesScope() {
    super(NAME, new FilteredPackageSet(NAME) {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull Project project) {
        // do not include fake-files e.g. fragment-editors, database consoles, etc.
        if (file.getFileSystem() instanceof NonPhysicalFileSystem) return false;
        if (!file.isInLocalFileSystem()) return true;
        if (isInsideProjectContent(project, file)) return false;
        return !ProjectScope.getProjectScope(project).contains(file);
      }
    });
  }

  private static boolean isInsideProjectContent(@NotNull Project project, @NotNull VirtualFile file) {
    if (!file.isInLocalFileSystem()) {
      final String projectBaseDir = project.getBasePath();
      if (projectBaseDir != null) {
        return FileUtil.isAncestor(projectBaseDir, file.getPath(), false);
      }
    }
    return false;
  }

  @NotNull
  @Deprecated
  public static NamedScope[] removeFromList(@NotNull NamedScope[] scopes) {
    int nonProjectIdx = -1;
    for (int i = 0, length = scopes.length; i < length; i++) {
      NamedScope scope = scopes[i];
      if (scope instanceof NonProjectFilesScope) {
        nonProjectIdx = i;
        break;
      }
    }
    if (nonProjectIdx > -1) {
      scopes = ArrayUtil.remove(scopes, nonProjectIdx);
    }
    return scopes;
  }
}
