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
package com.intellij.ide;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class GeneratedSourceFileChangeTracker extends AbstractProjectComponent {
  @NotNull
  public static GeneratedSourceFileChangeTracker getInstance(@NotNull Project project) {
    return project.getComponent(GeneratedSourceFileChangeTracker.class);
  }

  protected GeneratedSourceFileChangeTracker(Project project) {
    super(project);
  }

  public abstract boolean isEditedGeneratedFile(@NotNull VirtualFile file);
}
