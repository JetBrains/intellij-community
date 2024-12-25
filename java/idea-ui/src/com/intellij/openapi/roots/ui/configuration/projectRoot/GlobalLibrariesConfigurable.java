// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public @Nls String getDisplayName() {
    return JavaUiBundle.message("configurable.GlobalLibrariesConfigurable.display.name");
  }

  @Override
  public @NotNull @NonNls String getId() {
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
