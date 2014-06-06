/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class UniqueVFilePathBuilder {
  private static final UniqueVFilePathBuilder DUMMY_BUILDER = new UniqueVFilePathBuilder() {
    @NotNull
    @Override
    public String getUniqueVirtualFilePath(Project project, VirtualFile vFile) {
      return vFile.getPresentableName();
    }

    @NotNull
    @Override
    public String getUniqueVirtualFilePathWithinOpenedFileEditors(Project project, VirtualFile vFile) {
      return vFile.getPresentableName();
    }
  };

  public static UniqueVFilePathBuilder getInstance() {
    final UniqueVFilePathBuilder service = ServiceManager.getService(UniqueVFilePathBuilder.class);
    if (service == null) {
      return DUMMY_BUILDER;
    }
    return service;
  }

  @NotNull
  public abstract String getUniqueVirtualFilePath(Project project, VirtualFile vFile);

  @NotNull
  public abstract String getUniqueVirtualFilePathWithinOpenedFileEditors(Project project, VirtualFile vFile);
}
