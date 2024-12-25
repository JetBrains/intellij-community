// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.Unmodifiable;

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
  public @Nls String getDisplayName() {
    return JavaUiBundle.message("configurable.ProjectLibrariesConfigurable.display.name");
  }

  @Override
  public @NotNull @NonNls String getId() {
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

  @Override
  protected @NotNull @Unmodifiable List<? extends AnAction> createCopyActions(boolean fromPopup) {
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
