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
package com.intellij.ide.util.frameworkSupport;

import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import org.jetbrains.annotations.NotNull;

public class FrameworkSupportModelImpl extends FrameworkSupportModelBase {
  private final String myContentRootPath;

  public FrameworkSupportModelImpl(@NotNull final Project project,
                                   @NotNull String baseDirectoryForLibrariesPath,
                                   @NotNull LibrariesContainer librariesContainer) {
    super(project, null, librariesContainer);
    myContentRootPath = baseDirectoryForLibrariesPath;
  }

  @NotNull
  @Override
  public String getBaseDirectoryForLibrariesPath() {
    return myContentRootPath;
  }
}
