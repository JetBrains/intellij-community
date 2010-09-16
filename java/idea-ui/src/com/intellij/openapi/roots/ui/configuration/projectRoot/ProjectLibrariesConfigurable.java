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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@State(
  name = "ProjectLibrariesConfigurable.UI",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class ProjectLibrariesConfigurable extends BaseLibrariesConfigurable {

  public ProjectLibrariesConfigurable(final Project project) {
    super(project);
    myLevel = LibraryTablesRegistrar.PROJECT_LEVEL;
  }

  @Nls
  public String getDisplayName() {
    return "Libraries";
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @NonNls
  public String getId() {
    return "project.libraries";
  }


  public StructureLibraryTableModifiableModelProvider getModelProvider() {
    return myContext.getProjectLibrariesProvider();
  }

  public BaseLibrariesConfigurable getOppositeGroup() {
    return GlobalLibrariesConfigurable.getInstance(myProject);
  }

  public static ProjectLibrariesConfigurable getInstance(final Project project) {
    return ServiceManager.getService(project, ProjectLibrariesConfigurable.class);
  }

  protected String getAddText() {
    return ProjectBundle.message("add.new.project.library.text");
  }
}
