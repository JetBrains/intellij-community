package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
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
    return myFacade.findIdeModule(module, ideProject);
  }

  @Nullable
  public Module findIdeModule(@NotNull String ideModuleName, @NotNull Project ideProject) {
    return myFacade.findIdeModule(ideModuleName, ideProject);
  }

  @Nullable
  public Library findIdeLibrary(@NotNull final LibraryData libraryData, @NotNull Project ideProject) {
    return myFacade.findIdeLibrary(libraryData, ideProject);
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
    return myFacade.findIdeModuleDependency(dependency, model);
  }

  @Nullable
  public OrderEntry findIdeModuleOrderEntry(LibraryDependencyData data, Project project) {
    return myFacade.findIdeModuleOrderEntry(data, project);
  }
}
