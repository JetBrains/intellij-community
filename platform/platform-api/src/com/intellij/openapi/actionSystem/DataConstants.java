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

import org.jetbrains.annotations.NonNls;

/**
 * Identifiers for data items which can be returned from {@link DataContext#getData(String)} and
 * {@link DataProvider#getData(String)}.
 *
 * @deprecated {@link DataKeys} and {@link DataKey#getData} should be used instead
 */
@Deprecated
@SuppressWarnings({"HardCodedStringLiteral", "JavadocReference"})
public interface DataConstants {
  /**
   * Returns {@link com.intellij.openapi.project.Project}
   *
   * @deprecated use {@link PlatformDataKeys#PROJECT} instead
   */
  @Deprecated String PROJECT = CommonDataKeys.PROJECT.getName();

  /**
   * Returns {@link com.intellij.openapi.module.Module}
   *
   * @deprecated use {@link com.intellij.openapi.actionSystem.LangDataKeys#MODULE} instead
   */
  @Deprecated @NonNls String MODULE = "module";

  /**
   * Returns {@link com.intellij.openapi.vfs.VirtualFile}
   *
   * @deprecated use {@link PlatformDataKeys#VIRTUAL_FILE} instead
   */
  @Deprecated String VIRTUAL_FILE = CommonDataKeys.VIRTUAL_FILE.getName();

  /**
   * Returns array of {@link com.intellij.openapi.vfs.VirtualFile}
   *
   * @deprecated use {@link PlatformDataKeys#VIRTUAL_FILE_ARRAY} instead
   */
  @Deprecated String VIRTUAL_FILE_ARRAY = CommonDataKeys.VIRTUAL_FILE_ARRAY.getName();

  /**
   * Returns {@link com.intellij.openapi.editor.Editor}
   *
   * @deprecated use {@link PlatformDataKeys#EDITOR} instead
   */
  @Deprecated String EDITOR = CommonDataKeys.EDITOR.getName();

  /**
   * Returns {@link com.intellij.openapi.fileEditor.FileEditor}
   *
   * @deprecated use {@link PlatformDataKeys#FILE_EDITOR} instead
   */
  @Deprecated String FILE_EDITOR = PlatformDataKeys.FILE_EDITOR.getName();
}
