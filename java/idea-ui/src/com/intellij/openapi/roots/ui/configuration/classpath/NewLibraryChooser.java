// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.*;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.CreateNewLibraryAction;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.CreateNewLibraryDialog;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

class NewLibraryChooser implements ClasspathElementChooser<Library> {
  private final ModifiableRootModel myRootModel;
  private final StructureConfigurableContext myContext;
  private final JComponent myParentComponent;
  private final Project myProject;
  private final LibraryType myLibraryType;

  NewLibraryChooser(final Project project,
                           final ModifiableRootModel rootModel,
                           LibraryType libraryType, StructureConfigurableContext context, final JComponent parentComponent) {
    myRootModel = rootModel;
    myLibraryType = libraryType;
    myContext = context;
    myParentComponent = parentComponent;
    myProject = project;
  }

  @Override
  public @NotNull @Unmodifiable List<Library> chooseElements() {
    return ContainerUtil.createMaybeSingletonList(createLibrary());
  }

  public @Nullable Library createLibrary() {
    final NewLibraryConfiguration configuration =
      CreateNewLibraryAction.createNewLibraryConfiguration(myLibraryType, myParentComponent, myProject);
    if (configuration == null) return null;

    final NewLibraryEditor libraryEditor = new NewLibraryEditor(configuration.getLibraryType(), configuration.getProperties());
    libraryEditor.setName(configuration.getDefaultLibraryName());
    configuration.addRoots(libraryEditor);

    final LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
    List<LibraryTable> tables = Arrays.asList(myRootModel.getModuleLibraryTable(),
                                              registrar.getLibraryTable(myProject),
                                              registrar.getLibraryTable());

    CreateNewLibraryDialog dialog = new CreateNewLibraryDialog(myParentComponent, myContext, libraryEditor, tables, 1);
    final Module contextModule = LangDataKeys.MODULE_CONTEXT.getData(DataManager.getInstance().getDataContext(myParentComponent));
    dialog.setContextModule(contextModule);
    if (dialog.showAndGet()) {
      return dialog.createLibrary();
    }
    return null;
  }
}