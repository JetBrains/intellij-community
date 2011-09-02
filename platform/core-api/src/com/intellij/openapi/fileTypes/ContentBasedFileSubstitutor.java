/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ContentBasedFileSubstitutor {
  ExtensionPointName<ContentBasedFileSubstitutor> EP_NAME = ExtensionPointName.create("com.intellij.contentBasedClassFileProcessor");

  /**
   * Checks whether appropriate specific activity is available on given file
   */
  boolean isApplicable(Project project, VirtualFile vFile);

  /**
   * @return specific text representation of compiled classfile
   */
  @NotNull
  String obtainFileText(Project project, VirtualFile file);

  /**
   * @return language for compiled classfile
   */
  @Nullable
  Language obtainLanguageForFile(VirtualFile file);
}
