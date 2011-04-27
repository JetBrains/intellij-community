package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;

/**
 * @author Dennis.Ushakov
 */
public class PlatformModifiableModelsProvider implements ModifiableModelsProvider {
  public ModifiableRootModel getModuleModifiableModel(final Module module) {
    return ModuleRootManager.getInstance(module).getModifiableModel();
  }

  public void commitModuleModifiableModel(final ModifiableRootModel model) {
    model.commit();
  }

  public void disposeModuleModifiableModel(final ModifiableRootModel model) {
    model.dispose();
  }

  public LibraryTable.ModifiableModel getLibraryTableModifiableModel() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
  }

  @Override
  public LibraryTable.ModifiableModel getLibraryTableModifiableModel(Project project) {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(project).getModifiableModel();
  }
}
