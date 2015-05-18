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

/**
 * Files for which #isTestData returns true won't be processed by
 * Optimize Imports, Reformat Code and Rearrange Code actions during before-commit stage.
 *
 * When developing language plugin it is improperly to perform any modification actions
 * for language test data files (files that looks like source code, but are used only as test data)
 * on commit, even if appropriate checkboxes in commit dialog are turned on.
 */
public interface LanguageTestDataChecker {

  ExtensionPointName<LanguageTestDataChecker> EP_NAME =
    new ExtensionPointName<LanguageTestDataChecker>("com.intellij.languageTestDataChecker");

  @NotNull
  FileType getFileType();

  boolean isTestData(@NotNull Project project, @NotNull VirtualFile virtualFile);

}
