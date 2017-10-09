/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.openapi.fileTypes.FileTypeExtension;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class TodoIndexers extends FileTypeExtension<DataIndexer<TodoIndexEntry, Integer, FileContent>> {
  public static final TodoIndexers INSTANCE = new TodoIndexers();

  private TodoIndexers() {
    super("com.intellij.todoIndexer");
  }

  public static boolean needsTodoIndex(@NotNull VirtualFile file) {
    if (!file.isInLocalFileSystem()) {
      return false;
    }
    if (!isInContentOfAnyProject(file)) {
      return false;
    }
    return true;
  }

  private static boolean isInContentOfAnyProject(@NotNull VirtualFile file) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (ProjectFileIndex.getInstance(project).isInContent(file)) {
        return true;
      }
    }
    return false;
  }
}
