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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@State(
  name = "GlobalLibrariesConfigurable.UI",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class GlobalLibrariesConfigurable extends BaseLibrariesConfigurable {

  public GlobalLibrariesConfigurable(final Project project) {
    super(project);
    myLevel = LibraryTablesRegistrar.APPLICATION_LEVEL;
  }

  @Nls
  public String getDisplayName() {
    return "Global Libraries";
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @NonNls
  public String getId() {
    return "global.libraries";
  }


  public static GlobalLibrariesConfigurable getInstance(final Project project) {
    return ServiceManager.getService(project, GlobalLibrariesConfigurable.class);
  }

  public StructureLibraryTableModifiableModelProvider getModelProvider() {
    return myContext.getGlobalLibrariesProvider();
  }

  public BaseLibrariesConfigurable getOppositeGroup() {
    return ProjectLibrariesConfigurable.getInstance(myProject);
  }

  protected String getAddText() {
    return ProjectBundle.message("add.new.global.library.text");
  }
}