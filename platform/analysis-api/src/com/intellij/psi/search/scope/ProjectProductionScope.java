/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.AbstractPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class ProjectProductionScope extends NamedScope {
  public static final String NAME = IdeBundle.message("predefined.scope.production.name");

  public ProjectProductionScope() {
    super(NAME, new AbstractPackageSet("src:*..*") {
      @Override
      public boolean contains(VirtualFile file, NamedScopesHolder holder) {
        return contains(file, holder.getProject(), holder);
      }

      @Override
      public boolean contains(VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
        final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        return file != null
               && index.isInSource(file)
               && !index.isInTestSourceContent(file)
               && !index.isInLibraryClasses(file)
               && !index.isInLibrarySource(file);
      }
    });
  }
}
