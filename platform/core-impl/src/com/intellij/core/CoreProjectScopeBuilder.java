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
package com.intellij.core;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScopeBuilder;
import com.intellij.psi.search.ProjectScopeImpl;

/**
 * @author yole
 */
public class CoreProjectScopeBuilder extends ProjectScopeBuilder {
  private final Project myProject;
  private final FileIndexFacade myFileIndexFacade;

  public CoreProjectScopeBuilder(Project project, FileIndexFacade fileIndexFacade) {
    myFileIndexFacade = fileIndexFacade;
    myProject = project;
  }

  @Override
  public GlobalSearchScope buildLibrariesScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope buildAllScope() {
    return new EverythingGlobalScope();
  }

  @Override
  public GlobalSearchScope buildProjectScope() {
    return new ProjectScopeImpl(myProject, myFileIndexFacade);
  }
}
