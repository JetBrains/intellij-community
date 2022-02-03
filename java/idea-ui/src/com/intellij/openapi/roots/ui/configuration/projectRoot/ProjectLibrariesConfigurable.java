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

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ProjectLibrariesConfigurable extends BaseLibrariesConfigurable {
  public ProjectLibrariesConfigurable(ProjectStructureConfigurable projectStructureConfigurable) {
    super(projectStructureConfigurable, LibraryTablesRegistrar.PROJECT_LEVEL);
  }

  @Override
  protected String getComponentStateKey() {
    return "ProjectLibrariesConfigurable.UI";
  }

  @Override
  @Nls
  public String getDisplayName() {
    return JavaUiBundle.message("configurable.ProjectLibrariesConfigurable.display.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getId() {
    return "project.libraries";
  }


  @Override
  public StructureLibraryTableModifiableModelProvider getModelProvider() {
    return myContext.getProjectLibrariesProvider();
  }

  @Override
  public BaseLibrariesConfigurable getOppositeGroup() {
    return myProjectStructureConfigurable.getGlobalLibrariesConfigurable();
  }

  @Override
  public LibraryTablePresentation getLibraryTablePresentation() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getPresentation();
  }

  @NotNull
  @Override
  protected List<? extends AnAction> createCopyActions(boolean fromPopup) {
    List<? extends AnAction> actions = super.createCopyActions(fromPopup);
    if (fromPopup) {
      return ContainerUtil.concat(actions, Collections.singletonList(new ConvertProjectLibraryToRepositoryLibraryAction(this, myContext)));
    }
    return actions;
  }

  @Override
  protected String getAddText() {
    return JavaUiBundle.message("add.new.project.library.text");
  }
}
