/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

public class JavaOutOfSourcesChecker implements OutOfSourcesChecker {

  @Override
  @NotNull
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  public boolean isOutOfSources(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    return !index.isUnderSourceRootOfType(virtualFile, JavaModuleSourceRootTypes.SOURCES);
  }
}
