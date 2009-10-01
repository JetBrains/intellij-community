/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureDaemonAnalyzer;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class StructureConfigurableContext implements Disposable {
  private final ProjectStructureDaemonAnalyzer myDaemonAnalyzer;
  private static final Logger LOG = Logger.getInstance("#" + StructureConfigurableContext.class.getName());

  public enum ValidityLevel { VALID, WARNING, ERROR }

  public static final String NO_JDK = ProjectBundle.message("project.roots.module.jdk.problem.message");
  public static final String DUPLICATE_MODULE_NAME = ProjectBundle.message("project.roots.module.duplicate.name.message");
  @NonNls public static final String DELETED_LIBRARIES = "lib";

  public final Map<String, Set<String>> myLibraryDependencyCache = new HashMap<String, Set<String>>();
  public final Map<Sdk, Set<String>> myJdkDependencyCache = new HashMap<Sdk, Set<String>>();
  public final Map<Module, Map<String, Set<String>>> myValidityCache = new HashMap<Module, Map<String, Set<String>>>();
  public final Map<Library, ValidityLevel> myLibraryPathValidityCache = new HashMap<Library, ValidityLevel>(); //can be invalidated on startup only
  public final Map<Module, Set<String>> myModulesDependencyCache = new HashMap<Module, Set<String>>();

  private final ModuleManager myModuleManager;
  public final ModulesConfigurator myModulesConfigurator;
  public final Map<String, LibrariesModifiableModel> myLevel2Providers = new THashMap<String, LibrariesModifiableModel>();
  private final Project myProject;


  public StructureConfigurableContext(Project project, final ModulesConfigurator modulesConfigurator) {
    myProject = project;
    myModulesConfigurator = modulesConfigurator;
    Disposer.register(project, this);
    myDaemonAnalyzer = new ProjectStructureDaemonAnalyzer(this);
  }

  public Project getProject() {
    return myProject;
  }

  public ProjectStructureDaemonAnalyzer getDaemonAnalyzer() {
    return myDaemonAnalyzer;
  }

  @Nullable
  public Set<String> getCachedDependencies(final Object selectedObject, boolean force) {
    if (selectedObject instanceof Library){
      final Library library = (Library)selectedObject;
      if (myLibraryDependencyCache.containsKey(library.getName())){
        return myLibraryDependencyCache.get(library.getName());
      }
    } else if (selectedObject instanceof Sdk){
      final Sdk projectJdk = (Sdk)selectedObject;
      if (myJdkDependencyCache.containsKey(projectJdk)){
        return myJdkDependencyCache.get(projectJdk);
      }
    } else if (selectedObject instanceof Module) {
      final Module module = (Module)selectedObject;
      if (myModulesDependencyCache.containsKey(module)) {
        return myModulesDependencyCache.get(module);
      }
    }
    if (force){
      LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
      final Set<String> dep = getDependencies(selectedObject);
      updateCache(selectedObject, dep);
      return dep;
    } else {
      myUpdateDependenciesAlarm.addRequest(new Runnable(){
        public void run() {
          final Set<String> dep = getDependencies(selectedObject);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (!myDisposed) {
                updateCache(selectedObject, dep);
                fireOnCacheChanged();
              }
            }
          });
        }
      }, 100);
      return null;
    }
  }

  private void updateCache(final Object selectedObject, final Set<String> dep) {
    if (selectedObject instanceof Library) {
      myLibraryDependencyCache.put(((Library)selectedObject).getName(), dep);
    }
    else if (selectedObject instanceof Sdk) {
      myJdkDependencyCache.put((Sdk)selectedObject, dep);
    }
    else if (selectedObject instanceof Module) {
      myModulesDependencyCache.put((Module)selectedObject, dep);
    }
  }

  private Set<String> getDependencies(final Condition<OrderEntry> condition) {
    final Set<String> result = new TreeSet<String>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final Module[] modules = myModulesConfigurator.getModules();
        for (final Module module : modules) {
          final ModuleEditor moduleEditor = myModulesConfigurator.getModuleEditor(module);
          if (moduleEditor != null) {
            final OrderEntry[] entries = moduleEditor.getOrderEntries();
            for (OrderEntry entry : entries) {
              if (myDisposed) return;
              if (condition.value(entry)) {
                result.add(module.getName());
                break;
              }
            }
          }
        }
      }
    });
    return result;
  }

  @Nullable
  private Set<String> getDependencies(final Object selectedObject) {
    if (selectedObject instanceof Module) {
      return getDependencies(new Condition<OrderEntry>() {
        public boolean value(final OrderEntry orderEntry) {
          return orderEntry instanceof ModuleOrderEntry && Comparing.equal(((ModuleOrderEntry)orderEntry).getModule(), selectedObject);
        }
      });
    }
    else if (selectedObject instanceof Library) {
      Library library = (Library)selectedObject;
      if (library.getTable() == null) { //module library navigation
        HashSet<String> deps = new HashSet<String>();
        Module module = ((LibraryImpl)library).getModule();
        if (module != null) {
          deps.add(module.getName());
        }
        return deps;
      }
      return getDependencies(new Condition<OrderEntry>() {
        @SuppressWarnings({"SimplifiableIfStatement"})
        public boolean value(final OrderEntry orderEntry) {
          if (orderEntry instanceof LibraryOrderEntry){
            final LibraryImpl library = (LibraryImpl)((LibraryOrderEntry)orderEntry).getLibrary();
            if (Comparing.equal(library, selectedObject)) return true;
            return library != null && Comparing.equal(library.getSource(), selectedObject);
          }
          return false;
        }
      });
    }
    else if (selectedObject instanceof Sdk) {
      return getDependencies(new Condition<OrderEntry>() {
        public boolean value(final OrderEntry orderEntry) {
          return orderEntry instanceof JdkOrderEntry && Comparing.equal(((JdkOrderEntry)orderEntry).getJdk(), selectedObject);
        }
      });
    }
    return null;
  }

  public void dispose() {
    clearCaches(true);
    myDisposed = true;
  }

  public void invalidateModules(final Set<String> modules) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (modules != null) {
          for (String module : modules) {
            myValidityCache.remove(myModuleManager.findModuleByName(module));
          }
        }
      }
    });
  }
  public void invalidateModuleName(final Module module) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        final Map<String, Set<String>> problems = myValidityCache.remove(module);
        if (problems != null) {
          fireOnCacheChanged();            
        }
      }
    });
  }

  public ModulesConfigurator getModulesConfigurator() {
    return myModulesConfigurator;
  }

  public void clearCaches(final boolean cleanCacheUpdaters) {
    myJdkDependencyCache.clear();
    myLibraryDependencyCache.clear();
    myValidityCache.clear();
    myLibraryPathValidityCache.clear();
    myModulesDependencyCache.clear();

    fireOnCacheChanged();
    if (cleanCacheUpdaters) {
      myCacheUpdaters.clear();
    }
  }

  public void clearCaches(final Module module, final List<Library> chosen) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    for (Library library : chosen) {
      myLibraryDependencyCache.remove(library.getName());
    }
    myValidityCache.remove(module);
    fireOnCacheChanged();
  }

  public void clearCaches(final Module module, final Sdk oldJdk, final Sdk selectedModuleJdk) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    myJdkDependencyCache.remove(oldJdk);
    myJdkDependencyCache.remove(selectedModuleJdk);
    myValidityCache.remove(module);
    fireOnCacheChanged();
  }

  public void clearCaches(final OrderEntry entry) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    if (entry instanceof ModuleOrderEntry) {
      final Module module = ((ModuleOrderEntry)entry).getModule();
      myValidityCache.remove(module);
      myModulesDependencyCache.remove(module);
    } else if (entry instanceof JdkOrderEntry) {
      invalidateModules(myJdkDependencyCache.remove(((JdkOrderEntry)entry).getJdk()));
    } else if (entry instanceof LibraryOrderEntry) {
      invalidateModules(myLibraryDependencyCache.remove(((LibraryOrderEntry)entry).getLibraryName()));
    }
    fireOnCacheChanged();
  }

  public ValidityLevel isInvalid(final Object object) {
     if (object instanceof Module){
       final Module module = (Module)object;
       if (myValidityCache.containsKey(module)) {
         boolean valid = myValidityCache.get(module) == null;
         return valid ? ValidityLevel.VALID : ValidityLevel.ERROR;
       }
       myUpdateDependenciesAlarm.addRequest(new Runnable(){
         public void run() {
           ApplicationManager.getApplication().runReadAction(new Runnable() {
             public void run() {
               updateModuleValidityCache(module);
             }
           });
         }
       }, 100);
     } else if (object instanceof LibraryEx) {
       final LibraryEx library = (LibraryEx)object;
       if (myLibraryPathValidityCache.containsKey(library)) return myLibraryPathValidityCache.get(library);
       myUpdateDependenciesAlarm.addRequest(new Runnable(){
         public void run() {
           ApplicationManager.getApplication().runReadAction(new Runnable() {
             public void run() {
               updateLibraryValidityCache(library);
             }
           });
         }
       }, 100);
     }
     return ValidityLevel.VALID;
   }

   private void updateLibraryValidityCache(final LibraryEx library) {
     if (myLibraryPathValidityCache.containsKey(library)) return; //do not check twice
     ValidityLevel level = ValidityLevel.VALID;
     if (!(library.allPathsValid(JavadocOrderRootType.getInstance()) && library.allPathsValid(OrderRootType.SOURCES))) {
       level = ValidityLevel.WARNING;
     }
     if (!library.allPathsValid(OrderRootType.CLASSES)) {
       level = ValidityLevel.ERROR;
     }

     final ValidityLevel finalLevel = level;
     SwingUtilities.invokeLater(new Runnable(){
       public void run() {
         if (!myDisposed){
           myLibraryPathValidityCache.put(library, finalLevel);
           fireOnCacheChanged();
         }
       }
     });
   }

   private void updateModuleValidityCache(final Module module) {
     if (myValidityCache.containsKey(module)) return; //do not check twice

     if (myDisposed) return;

     Map<String, Set<String>> problems = null;
     final ModifiableModuleModel moduleModel = myModulesConfigurator.getModuleModel();
     final Module[] all = moduleModel.getModules();
     for (Module each : all) {
       if (each != module && getRealName(each).equals(getRealName(module))) {
         problems = new HashMap<String, Set<String>>();
         problems.put(DUPLICATE_MODULE_NAME, null);
         break;
       }
     }

     final ModuleRootModel rootModel = myModulesConfigurator.getRootModel(module);
     if (rootModel == null) return; //already disposed
     final OrderEntry[] entries = rootModel.getOrderEntries();
     for (OrderEntry entry : entries) {
       if (myDisposed) return;
       if (!entry.isValid()){
         if (problems == null) {
           problems = new HashMap<String, Set<String>>();
         }
         if (entry instanceof JdkOrderEntry && ((JdkOrderEntry)entry).getJdkName() == null) {
           problems.put(NO_JDK, null);
         } else {
           Set<String> deletedLibraries = problems.get(DELETED_LIBRARIES);
           if (deletedLibraries == null){
             deletedLibraries = new HashSet<String>();
             problems.put(DELETED_LIBRARIES, deletedLibraries);
           }
           deletedLibraries.add(entry.getPresentableName());
         }
       }
     }
     final Map<String, Set<String>> finalProblems = problems;
     SwingUtilities.invokeLater(new Runnable() {
       public void run() {
         if (!myDisposed) {
           myValidityCache.put(module, finalProblems);
           fireOnCacheChanged();
         }
       }
     });
   }

  public Module[] getModules() {
    return myModulesConfigurator.getModules();
  }

  public String getRealName(final Module module) {
    final ModifiableModuleModel moduleModel = myModulesConfigurator.getModuleModel();
    String newName = moduleModel.getNewName(module);
    return newName != null ? newName : module.getName();
  }

  public void resetLibraries() {
    final LibraryTablesRegistrar tablesRegistrar = LibraryTablesRegistrar.getInstance();

    myLevel2Providers.clear();
    myLevel2Providers.put(LibraryTablesRegistrar.APPLICATION_LEVEL, new LibrariesModifiableModel(tablesRegistrar.getLibraryTable(), myProject));
    myLevel2Providers.put(LibraryTablesRegistrar.PROJECT_LEVEL, new LibrariesModifiableModel(tablesRegistrar.getLibraryTable(myProject), myProject));
    for (final LibraryTable table : tablesRegistrar.getCustomLibraryTables()) {
      myLevel2Providers.put(table.getTableLevel(), new LibrariesModifiableModel(table, myProject));
    }
  }

  public LibraryTableModifiableModelProvider getGlobalLibrariesProvider(final boolean tableEditable) {
    return createModifiableModelProvider(LibraryTablesRegistrar.APPLICATION_LEVEL, tableEditable);
  }

  public LibraryTableModifiableModelProvider createModifiableModelProvider(final String level, final boolean isTableEditable) {
    final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(level, myProject);
    return new LibraryTableModifiableModelProvider() {
        public LibraryTable.ModifiableModel getModifiableModel() {
          return myLevel2Providers.get(level);
        }

        public String getTableLevel() {
          return table.getTableLevel();
        }

        public LibraryTablePresentation getLibraryTablePresentation() {
          return table.getPresentation();
        }

        public boolean isLibraryTableEditable() {
          return isTableEditable && table.isEditable();
        }
      };
  }

  public LibraryTableModifiableModelProvider getProjectLibrariesProvider(final boolean tableEditable) {
    return createModifiableModelProvider(LibraryTablesRegistrar.PROJECT_LEVEL, tableEditable);
  }


  public List<LibraryTableModifiableModelProvider> getCustomLibrariesProviders(final boolean tableEditable) {
    return ContainerUtil.map2List(LibraryTablesRegistrar.getInstance().getCustomLibraryTables(), new NotNullFunction<LibraryTable, LibraryTableModifiableModelProvider>() {
      @NotNull
      public LibraryTableModifiableModelProvider fun(final LibraryTable libraryTable) {
        return createModifiableModelProvider(libraryTable.getTableLevel(), tableEditable);
      }
    });
  }


  @Nullable
  public Library getLibrary(final String libraryName, final String libraryLevel) {
/* the null check is added only to prevent NPE when called from getLibrary */
    if (myLevel2Providers.isEmpty()) resetLibraries();
    final LibrariesModifiableModel model = myLevel2Providers.get(libraryLevel);
    return model == null ? null : findLibraryModel(libraryName, model);
  }

  @Nullable
  private static Library findLibraryModel(final String libraryName, @NotNull LibrariesModifiableModel model) {
    final Library library = model.getLibraryByName(libraryName);
    return findLibraryModel(library, model);
  }

  @Nullable
  private static Library findLibraryModel(final Library library, LibrariesModifiableModel tableModel) {
    if (tableModel == null) return library;
    if (tableModel.wasLibraryRemoved(library)) return null;
    return tableModel.hasLibraryEditor(library) ? (Library)tableModel.getLibraryEditor(library).getModel() : library;
  }


  public void reset() {
    myDaemonAnalyzer.reset();
    resetLibraries();
    myModulesConfigurator.resetModuleEditors();
  }
}
