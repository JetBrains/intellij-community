package com.intellij.openapi.roots;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.LibraryTable;

/**
 * Returns the modifiable models from either the open Project Structure configurable (if any) or the standard module root manager.
 *
 * @author Dennis.Ushakov
 */
public interface ModifiableModelsProvider {
  class SERVICE {
    private SERVICE() {
    }

    public static ModifiableModelsProvider getInstance() {
      return ServiceManager.getService(ModifiableModelsProvider.class);
    }
  }
  
  ModifiableRootModel getModuleModifiableModel(final Module module);
  void commitModuleModifiableModel(final ModifiableRootModel model);
  void disposeModuleModifiableModel(final ModifiableRootModel model);

  LibraryTable.ModifiableModel getLibraryTableModifiableModel();
}
