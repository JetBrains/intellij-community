// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public final class PlatformModifiableModelsProvider implements ModifiableModelsProvider {
  @Override
  public ModifiableRootModel getModuleModifiableModel(final @NotNull Module module) {
    return ModuleRootManager.getInstance(module).getModifiableModel();
  }

  @Override
  public void commitModuleModifiableModel(final @NotNull ModifiableRootModel model) {
    model.commit();
  }

  @Override
  public void disposeModuleModifiableModel(final @NotNull ModifiableRootModel model) {
    model.dispose();
  }

  @Override
  public @NotNull ModifiableFacetModel getFacetModifiableModel(@NotNull Module module) {
    return FacetManager.getInstance(module).createModifiableModel();
  }

  @Override
  public void commitFacetModifiableModel(@NotNull Module module, @NotNull ModifiableFacetModel model) {
    model.commit();
  }

  @Override
  public @NotNull LibraryTable.ModifiableModel getLibraryTableModifiableModel() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
  }

  @Override
  public LibraryTable.ModifiableModel getLibraryTableModifiableModel(@NotNull Project project) {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(project).getModifiableModel();
  }

  @Override
  public void disposeLibraryTableModifiableModel(@NotNull LibraryTable.ModifiableModel model) {
    Disposer.dispose(model);
  }
}
