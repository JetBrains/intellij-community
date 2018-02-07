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

package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.project.Project;

/**
 * @author ven
 * */

public abstract class ProjectTreeStructure extends AbstractProjectTreeStructure {
  private final String myId;

  public ProjectTreeStructure(Project project, final String ID) {
    super(project);
    myId = ID;
  }

  @Override
  public boolean isFlattenPackages() {
    return ProjectView.getInstance(myProject).isFlattenPackages(myId);
  }

  @Override
  public boolean isShowMembers() {
    return ProjectView.getInstance(myProject).isShowMembers(myId);
  }

  @Override
  public boolean isHideEmptyMiddlePackages() {
    return ProjectView.getInstance(myProject).isHideEmptyMiddlePackages(myId);
  }

  @Override
  public boolean isAbbreviatePackageNames() {
    return ProjectView.getInstance(myProject).isAbbreviatePackageNames(myId);
  }

  @Override
  public boolean isShowLibraryContents() {
    return ProjectView.getInstance(myProject).isShowLibraryContents(myId);
  }

  @Override
  public boolean isShowModules() {
    return ProjectView.getInstance(myProject).isShowModules(myId);
  }

  @Override
  public boolean isFlattenModules() {
    return ProjectView.getInstance(myProject).isFlattenModules(myId);
  }

  @Override
  public boolean isShowURL() {
    return ProjectView.getInstance(myProject).isShowURL(myId);
  }
}