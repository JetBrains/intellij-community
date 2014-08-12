/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.search.scope;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.scope.packageSet.AbstractPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.Colored;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
@Colored(color = "ffffe4", darkVariant = "494539")
public class NonProjectFilesScope extends NamedScope {
  public static final String NAME = "Non-Project Files";

  public NonProjectFilesScope() {
    super(NAME, new AbstractPackageSet("NonProject") {
      @Override
      public boolean contains(VirtualFile file, NamedScopesHolder holder) {
        return contains(file, holder.getProject(), holder);
      }

      @Override
      public boolean contains(VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
        // do not include fake-files e.g. fragment-editors, database consoles, etc.
        if (file == null || file.getFileSystem() instanceof NonPhysicalFileSystem) return false;
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
