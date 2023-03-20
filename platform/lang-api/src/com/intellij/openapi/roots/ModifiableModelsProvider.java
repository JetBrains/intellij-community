// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;
import org.jetbrains.annotations.NotNull;

/**
 * Returns the modifiable models from either the open Project Structure configurable (if any) or the standard module root manager.
 *
 * @author Dennis.Ushakov
 */
public interface ModifiableModelsProvider {

  /**
   * @deprecated use {@link ModifiableModelsProvider#getInstance()} instead
   */
  @Deprecated(forRemoval = true)
  final class SERVICE {
    private SERVICE() {
    }

    public static ModifiableModelsProvider getInstance() {
      return ModifiableModelsProvider.getInstance();
    }
  }

  static ModifiableModelsProvider getInstance() {
    return ApplicationManager.getApplication().getService(ModifiableModelsProvider.class);
  }

  ModifiableRootModel getModuleModifiableModel(@NotNull Module module);
  void commitModuleModifiableModel(@NotNull ModifiableRootModel model);
  void disposeModuleModifiableModel(@NotNull ModifiableRootModel model);

  @NotNull
  ModifiableFacetModel getFacetModifiableModel(@NotNull Module module);
  void commitFacetModifiableModel(@NotNull Module module, @NotNull ModifiableFacetModel model);

  @NotNull
  LibraryTable.ModifiableModel getLibraryTableModifiableModel();
  LibraryTable.ModifiableModel getLibraryTableModifiableModel(@NotNull Project project);
  void disposeLibraryTableModifiableModel(@NotNull LibraryTable.ModifiableModel model);
}
