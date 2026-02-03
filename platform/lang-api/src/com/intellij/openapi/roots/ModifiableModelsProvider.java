// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;
import org.jetbrains.annotations.ApiStatus;
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

  /**
   * Returns the application-level (aka "global") library table selected by the eel environment
   * associated with the given {@code project}. The {@code project} parameter is used only to
   * determine the environment (via the project's already-resolved {@code EelMachine}); the returned table
   * is NOT a project-level table and may be used across projects that share the same environment.
   * <p>
   * Environment selection notes:
   * <ul>
   *   <li>Projects belonging to different eel environments see different sets of application-level libraries.</li>
   *   <li>This method does not perform any suspend/async operations; it relies on the environment
   *       already bound to the provided {@code project}.</li>
   * </ul>
   *
   * @param project a non-disposed project whose bound eel environment is used to choose the
   *                application-level library table.
   * @return the environment-specific application-level library table.
   * @see com.intellij.openapi.roots.libraries.LibraryTablesRegistrar#getGlobalLibraryTable(Project)
   */
  @ApiStatus.Experimental
  @NotNull
  LibraryTable.ModifiableModel getGlobalLibraryTableModifiableModel(@NotNull Project project);
  LibraryTable.ModifiableModel getLibraryTableModifiableModel(@NotNull Project project);
  void disposeLibraryTableModifiableModel(@NotNull LibraryTable.ModifiableModel model);
}
