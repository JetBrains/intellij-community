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

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.scope.packageSet.AbstractPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.Colored;
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
        return containsImpl(file, project);
      }
    });
  }

  private static boolean containsImpl(@NotNull VirtualFile file,
                                      @NotNull Project project) {
    // do not include fake-files e.g. fragment-editors, etc.
    if (file == null || file.getFileSystem() instanceof NonPhysicalFileSystem) return false;
    if (!file.isInLocalFileSystem()) return true;
    if (ScratchUtil.isScratch(file)) return false;
    return !ProjectScope.getProjectScope(project).contains(file);
  }
}
