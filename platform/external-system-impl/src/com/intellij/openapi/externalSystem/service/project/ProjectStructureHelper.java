package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 2/6/12 3:28 PM
 */
public class ProjectStructureHelper {

  @NotNull private final PlatformFacade myFacade;

  public ProjectStructureHelper(@NotNull PlatformFacade facade) {
    myFacade = facade;
  }

  @Nullable
  public Module findIdeModule(@NotNull ModuleData module, @NotNull Project ideProject) {
    return findIdeModule(module.getInternalName(), ideProject);
  }

  @Nullable
  public Module findIdeModule(@NotNull String ideModuleName, @NotNull Project ideProject) {
    for (Module module : myFacade.getModules(ideProject)) {
      if (ideModuleName.equals(module.getName())) {
        return module;
      }
    }
    return null;
  }

  @Nullable
  public Library findIdeLibrary(@NotNull final LibraryData libraryData, @NotNull Project ideProject) {
    final LibraryTable libraryTable = myFacade.getProjectLibraryTable(ideProject);
    for (Library ideLibrary : libraryTable.getLibraries()) {
      if (ExternalSystemApiUtil.isRelated(ideLibrary, libraryData)) return ideLibrary;
    }
    return null;
  }

  public static boolean isOrphanProjectLibrary(@NotNull final Library library,
                                               @NotNull final Iterable<Module> ideModules) {
    RootPolicy<Boolean> visitor = new RootPolicy<Boolean>() {
      @Override
      public Boolean visitLibraryOrderEntry(LibraryOrderEntry ideDependency, Boolean value) {
        return !ideDependency.isModuleLevel() && library == ideDependency.getLibrary();
      }
    };
    for (Module module : ideModules) {
      for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
        if (entry.accept(visitor, false)) return false;
      }
    }
    return true;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public ModuleOrderEntry findIdeModuleDependency(@NotNull ModuleDependencyData dependency, @NotNull ModifiableRootModel model) {
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry candidate = (ModuleOrderEntry)entry;
        if (dependency.getInternalName().equals(candidate.getModuleName()) &&
            dependency.getScope().equals(candidate.getScope())) {
          return candidate;
        }
      }
    }
    return null;
  }
}
