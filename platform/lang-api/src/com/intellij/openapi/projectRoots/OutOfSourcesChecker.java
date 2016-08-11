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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface OutOfSourcesChecker {

  ExtensionPointName<OutOfSourcesChecker> EP_NAME = new ExtensionPointName<>("com.intellij.outOfSourcesChecker");

  @NotNull
  FileType getFileType();

  /**
   * During automatic code changes, like ones, performed before commit (Reformat, Rearrange code, Optimize imports),
   * we do not want to touch source files, which are not compiled or executed (like "test data" source files, used in tests).
   *
   * Provides information whether file is out of sources, so is not compiled or executed. If it is out of sources,
   * no automatic code changing actions would be performed on it.
   */
  boolean isOutOfSources(@NotNull Project project, @NotNull VirtualFile virtualFile);

}
