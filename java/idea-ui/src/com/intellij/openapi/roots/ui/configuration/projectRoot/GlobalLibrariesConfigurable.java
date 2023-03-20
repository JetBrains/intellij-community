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
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class GlobalLibrariesConfigurable extends BaseLibrariesConfigurable {
  public GlobalLibrariesConfigurable(ProjectStructureConfigurable projectStructureConfigurable) {
    super(projectStructureConfigurable, LibraryTablesRegistrar.APPLICATION_LEVEL);
  }

  @Override
  protected String getComponentStateKey() {
    return "GlobalLibrariesConfigurable.UI";
  }

  @Override
  @Nls
  public String getDisplayName() {
    return JavaUiBundle.message("configurable.GlobalLibrariesConfigurable.display.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getId() {
    return "global.libraries";
  }

  @Override
  public LibraryTablePresentation getLibraryTablePresentation() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable().getPresentation();
  }

  @Override
  public StructureLibraryTableModifiableModelProvider getModelProvider() {
    return myContext.getGlobalLibrariesProvider();
  }

  @Override
  public BaseLibrariesConfigurable getOppositeGroup() {
    return myProjectStructureConfigurable.getProjectLibrariesConfigurable();
  }

  @Override
  protected String getAddText() {
    return JavaUiBundle.message("add.new.global.library.text");
  }
}
