/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Rustam Vishnyakov
 */
@State(name = "ProjectPlainTextFileTypeManager")
public class ProjectPlainTextFileTypeManager extends PersistentFileSetManager {
  private final ProjectFileIndex myIndex;

  public ProjectPlainTextFileTypeManager(ProjectFileIndex projectFileIndex) {
    myIndex = projectFileIndex;
  }

  boolean hasProjectContaining(@NotNull VirtualFile file) {
    return myIndex.isInContent(file);
  }

  public static ProjectPlainTextFileTypeManager getInstance(Project project) {
    return ServiceManager.getService(project, ProjectPlainTextFileTypeManager.class);
  }
}
