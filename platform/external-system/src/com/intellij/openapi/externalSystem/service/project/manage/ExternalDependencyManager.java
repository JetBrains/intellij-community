package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;

import java.util.*;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/7/12 3:23 PM
 */
public class ExternalDependencyManager {

  @NotNull private final PlatformFacade     myPlatformFacade;
  @NotNull private final LibraryDataManager myLibraryManager;

  public ExternalDependencyManager(@NotNull PlatformFacade platformFacade, @NotNull LibraryDataManager manager) {
    myPlatformFacade = platformFacade;
    myLibraryManager = manager;
  }

  public void importDependency(@NotNull DependencyData dependency, @NotNull Module module, boolean synchronous) {
    importDependencies(Collections.singleton(dependency), module, synchronous);
  }

  public void importDependencies(@NotNull Iterable<DependencyData> dependencies, @NotNull Module module, boolean synchronous) {
    final List<ModuleDependencyData> moduleDependencies = new ArrayList<ModuleDependencyData>();
    final List<LibraryDependencyData> libraryDependencies = new ArrayList<LibraryDependencyData>();
    ExternalEntityVisitor visitor = new ExternalEntityVisitorAdapter() {
      @Override
      public void visit(@NotNull ModuleDependencyData dependency) {
        moduleDependencies.add(dependency);
      }

      @Override
      public void visit(@NotNull LibraryDependencyData dependency) {
        libraryDependencies.add(dependency);
      }
    };
    for (DependencyData dependency : dependencies) {
      dependency.invite(visitor);
    }
    importLibraryDependencies(libraryDependencies, module, synchronous);
    importModuleDependencies(moduleDependencies, module, synchronous);
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void importModuleDependencies(@NotNull final Collection<ModuleDependencyData> dependencies,
                                       @NotNull final Module module,
                                       boolean synchronous)
  {
    if (dependencies.isEmpty()) {
      return;
    }

    ExternalSystemUtil.executeProjectChangeAction(module.getProject(), dependencies, synchronous, new Runnable() {
      @Override
      public void run() {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
        try {
          final ProjectStructureHelper projectStructureHelper
            = ServiceManager.getService(module.getProject(), ProjectStructureHelper.class);
          for (ModuleDependencyData dependency : dependencies) {
            final String moduleName = dependency.getName();
            final Module intellijModule = projectStructureHelper.findIdeModule(moduleName, module.getProject());
            if (intellijModule == null) {
              assert false;
              continue;
            }
            else if (intellijModule.equals(module)) {
              // Gradle api returns recursive module dependencies (a module depends on itself) for 'gradle' project.
              continue;
            }

            ModuleOrderEntry orderEntry = projectStructureHelper.findIdeModuleDependency(dependency, moduleRootModel);
            if (orderEntry == null) {
              orderEntry = moduleRootModel.addModuleOrderEntry(intellijModule);
            }
            orderEntry.setScope(dependency.getScope());
            orderEntry.setExported(dependency.isExported());
          }
        }
        finally {
          moduleRootModel.commit();
        }
      }
    });
  }
  
  public void importLibraryDependencies(@NotNull final Iterable<LibraryDependencyData> dependencies,
                                        @NotNull final Module module,
                                        final boolean synchronous)
  {
    ExternalSystemUtil.executeProjectChangeAction(module.getProject(), dependencies, synchronous, new Runnable() {
      @Override
      public void run() {
        LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
        Set<LibraryData> librariesToImport = new HashSet<LibraryData>();
        for (LibraryDependencyData dependency : dependencies) {
          final Library library = libraryTable.getLibraryByName(dependency.getName());
          if (library == null) {
            librariesToImport.add(dependency.getTarget());
          }
        }
        if (!librariesToImport.isEmpty()) {
          myLibraryManager.importLibraries(librariesToImport, module.getProject(), synchronous);
        }

        for (LibraryDependencyData dependency : dependencies) {
          ProjectStructureHelper helper = ServiceManager.getService(module.getProject(), ProjectStructureHelper.class);
          ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
          final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
          try {
            libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
            final Library library = libraryTable.getLibraryByName(dependency.getName());
            if (library == null) {
              assert false;
              continue;
            }
            LibraryOrderEntry orderEntry = helper.findIdeLibraryDependency(dependency.getName(), moduleRootModel);
            if (orderEntry == null) {
              // We need to get the most up-to-date Library object due to our project model restrictions.
              orderEntry = moduleRootModel.addLibraryEntry(library);
            }
            orderEntry.setExported(dependency.isExported());
            orderEntry.setScope(dependency.getScope());
          }
          finally {
            moduleRootModel.commit();
          }
        }
      }
    });
  }

  public void removeDependency(@NotNull final ExportableOrderEntry dependency, boolean synchronous) {
    removeDependencies(Collections.singleton(dependency), synchronous);
  }
  
  @SuppressWarnings("MethodMayBeStatic")
  public void removeDependencies(@NotNull final Collection<? extends ExportableOrderEntry> dependencies, boolean synchronous) {
    if (dependencies.isEmpty()) {
      return;
    }

    for (final ExportableOrderEntry dependency : dependencies) {
      final Module module = dependency.getOwnerModule();
      ExternalSystemUtil.executeProjectChangeAction(module.getProject(), dependency, synchronous, new Runnable() {
        @Override
        public void run() {
          ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
          final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
          try {
            // The thing is that intellij created order entry objects every time new modifiable model is created,
            // that's why we can't use target dependency object as is but need to get a reference to the current
            // entry object from the model instead.
            for (OrderEntry entry : moduleRootModel.getOrderEntries()) {
              if (entry.getPresentableName().equals(dependency.getPresentableName())) {
                moduleRootModel.removeOrderEntry(entry);
                break;
              }
            }
          }
          finally {
            moduleRootModel.commit();
          } 
        }
      });
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void setScope(@NotNull final DependencyScope scope, @NotNull final ExportableOrderEntry dependency, boolean synchronous) {
    Project project = dependency.getOwnerModule().getProject();
    ExternalSystemUtil.executeProjectChangeAction(project, dependency, synchronous, new Runnable() {
      @Override
      public void run() {
        doForDependency(dependency, new Consumer<ExportableOrderEntry>() {
          @Override
          public void consume(ExportableOrderEntry entry) {
            entry.setScope(scope);
          }
        });
      }
    });
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void setExported(final boolean exported, @NotNull final ExportableOrderEntry dependency, boolean synchronous) {
    Project project = dependency.getOwnerModule().getProject();
    ExternalSystemUtil.executeProjectChangeAction(project, dependency, synchronous, new Runnable() {
      @Override
      public void run() {
        doForDependency(dependency, new Consumer<ExportableOrderEntry>() {
          @Override
          public void consume(ExportableOrderEntry entry) {
            entry.setExported(exported);
          }
        });
      }
    });
  }

  private static void doForDependency(@NotNull ExportableOrderEntry entry, @NotNull Consumer<ExportableOrderEntry> consumer) {
    // We need to get an up-to-date modifiable model to work with.
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(entry.getOwnerModule());
    final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
    try {
      // The thing is that intellij created order entry objects every time new modifiable model is created,
      // that's why we can't use target dependency object as is but need to get a reference to the current
      // entry object from the model instead.
      for (OrderEntry e : moduleRootModel.getOrderEntries()) {
        if (e instanceof ExportableOrderEntry && e.getPresentableName().equals(entry.getPresentableName())) {
          consumer.consume((ExportableOrderEntry)e);
          break;
        }
      }
    }
    finally {
      moduleRootModel.commit();
    }
  }
}
