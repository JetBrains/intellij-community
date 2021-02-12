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
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.ApiStatus;

/**
 * Identifiers for data items which can be returned from {@link DataContext#getData(String)} and
 * {@link DataProvider#getData(String)}.
 *
 * @deprecated use {@link DataKeys} and {@link DataKey#getData} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
public interface DataConstants {
  /**
   * Returns {@link com.intellij.openapi.project.Project}
   *
   * @deprecated use {@link PlatformDataKeys#PROJECT} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  String PROJECT = CommonDataKeys.PROJECT.getName();

  /**
   * Returns {@link com.intellij.openapi.vfs.VirtualFile}
   *
   * @deprecated use {@link PlatformDataKeys#VIRTUAL_FILE} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String VIRTUAL_FILE = CommonDataKeys.VIRTUAL_FILE.getName();

  /**
   * Returns array of {@link com.intellij.openapi.vfs.VirtualFile}
   *
   * @deprecated use {@link PlatformDataKeys#VIRTUAL_FILE_ARRAY} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String VIRTUAL_FILE_ARRAY = CommonDataKeys.VIRTUAL_FILE_ARRAY.getName();

  /**
   * Returns {@link com.intellij.openapi.editor.Editor}
   *
   * @deprecated use {@link PlatformDataKeys#EDITOR} instead
   */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String EDITOR = CommonDataKeys.EDITOR.getName();
}
