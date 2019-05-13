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
public class PlatformModifiableModelsProvider implements ModifiableModelsProvider {
  @Override
  public ModifiableRootModel getModuleModifiableModel(@NotNull final Module module) {
    return ModuleRootManager.getInstance(module).getModifiableModel();
  }

  @Override
  public void commitModuleModifiableModel(final ModifiableRootModel model) {
    model.commit();
  }

  @Override
  public void disposeModuleModifiableModel(final ModifiableRootModel model) {
    model.dispose();
  }

  @Override
  public ModifiableFacetModel getFacetModifiableModel(Module module) {
    return FacetManager.getInstance(module).createModifiableModel();
  }

  @Override
  public void commitFacetModifiableModel(Module module, ModifiableFacetModel model) {
    model.commit();
  }

  @Override
  public LibraryTable.ModifiableModel getLibraryTableModifiableModel() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
  }

  @Override
  public LibraryTable.ModifiableModel getLibraryTableModifiableModel(Project project) {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(project).getModifiableModel();
  }

  @Override
  public void disposeLibraryTableModifiableModel(LibraryTable.ModifiableModel model) {
    Disposer.dispose(model);
  }
}
