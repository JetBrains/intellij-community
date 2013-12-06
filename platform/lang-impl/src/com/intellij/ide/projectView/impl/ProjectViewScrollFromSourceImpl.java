/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.navigation.ScrollFromSourceProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class ProjectViewScrollFromSourceImpl implements ScrollFromSourceProvider {
  @Override
  public void scrollFromSource(Project project, VirtualFile file) {
    if (project == null) return;

    ProjectView projectView = ProjectView.getInstance(project);
    if (projectView instanceof ProjectViewImpl) {
      ((ProjectViewImpl)projectView).scrollFromSource();
    }
  }
}
