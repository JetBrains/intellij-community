/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.build;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author Vladislav.Soroka
 * @since 7/6/2016
 */
public class ProjectBuildScope extends BuildScopeImpl {
  private final Project myProject;

  public ProjectBuildScope(final Project project) {
    super(ContainerUtil.map(ModuleManager.getInstance(project).getModules(), ModuleBuildTarget::new));
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }
}
