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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class RootConfigurationAccessor {
  @Nullable
  public Library getLibrary(final Library library, final String libraryName, final String libraryLevel) {
    return library;
  }

  @Nullable
  public Sdk getSdk(final Sdk sdk, final String sdkName) {
    return sdk;
  }

  public Module getModule(final Module module, final String moduleName) {
    return module;
  }

  public Sdk getProjectSdk(Project project) {
    return ProjectRootManager.getInstance(project).getProjectJdk();
  }

  @Nullable
  public String getProjectSdkName(final Project project) {
    return ProjectRootManager.getInstance(project).getProjectJdkName();
  }
}