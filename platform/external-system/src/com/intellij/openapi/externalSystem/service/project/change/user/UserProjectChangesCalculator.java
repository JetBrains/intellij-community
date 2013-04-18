/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project.change.user;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.project.change.user.*;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.settings.UserProjectChanges;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Encapsulates functionality of managing
 * {@link UserProjectChanges#getUserProjectChanges() explicit project structure changes made by user}.
 * 
 * @author Denis Zhdanov
 * @since 2/19/13 9:27 AM
 */
public class UserProjectChangesCalculator {

  @NotNull
  private static final Function<String, UserProjectChange<?>> MODULE_ADDED = new Function<String, UserProjectChange<?>>() {
    @Override
    public UserProjectChange fun(String moduleName) {
      return new AddModuleUserChange(moduleName);
    }
  };

  @NotNull
  private static final Function<String, UserProjectChange<?>> MODULE_REMOVED = new Function<String, UserProjectChange<?>>() {
    @Override
    public UserProjectChange fun(String moduleName) {
      return new RemoveModuleUserChange(moduleName);
    }
  };

  @NotNull private final PlatformFacade         myFacade;
  @NotNull private final ProjectStructureHelper myProjectStructureHelper;

  @Nullable private ProjectData myLastProjectState;

  public UserProjectChangesCalculator(@NotNull PlatformFacade facade, @NotNull ProjectStructureHelper helper) {
    myFacade = facade;
    myProjectStructureHelper = helper;
  }

  /**
   * Resets 'last known ide project state' to the current one for the given project.
   *
   * @param project  target project
   * @return current ide project state
   */
  @Nullable
  public ProjectData updateCurrentProjectState(@NotNull Project project) {
    ProjectData state = buildCurrentIdeProject(project);
    myLastProjectState = state;
    filterOutdatedChanges(project);
    return state;
  }

  /**
   * This method is expected to be called every time manual project structure change is detected. It compares current project structure
   * with the {@link #updateCurrentProjectState(Project) previous one} and considers all changes between them as user-made.
   * <p/>
   * All are checked for validity and dropped if they are out of date.
   *
   * @param project
   */
  public void updateChanges(@NotNull Project project) {
    ProjectData lastProjectState = myLastProjectState;
    if (lastProjectState == null) {
      updateCurrentProjectState(project);
      return;
    }

    ProjectData currentProjectState = buildCurrentIdeProject(project);
    if (currentProjectState == null) {
      return;
    }

    Context context = new Context(lastProjectState, currentProjectState);

    buildModulePresenceChanges(context);
    buildDependencyPresenceChanges(context);
    filterOutdatedChanges(project);
    UserProjectChanges changes = UserProjectChanges.getInstance(project);
    context.currentChanges.addAll(changes.getUserProjectChanges());
    changes.setUserProjectChanges(context.currentChanges);
    myLastProjectState = currentProjectState;
  }

  /**
   * We  of project structure changes explicitly made by a user.
   * It's possible, however, that some change might become outdated. Example:
   * <pre>
   * <ol>
   *   <li>New module is added to a project;</li>
   *   <li>Corresponding change is created;</li>
   *   <li>The module is removed from the project;</li>
   *   <li>The change is out of date now;</li>
   * </ol>
   * </pre>
   * This method removes all such outdated changes.
   * 
   * @param project  target ide project
   */
  public void filterOutdatedChanges(@NotNull Project project) {
    UserProjectChanges changesHolder = UserProjectChanges.getInstance(project);
    Set<UserProjectChange<?>> changes = ContainerUtilRt.newHashSet(changesHolder.getUserProjectChanges());
    for (UserProjectChange change : changesHolder.getUserProjectChanges()) {
      if (!isUpToDate(change, project)) {
        changes.remove(change);
      }
    }
    changesHolder.setUserProjectChanges(changes);
  }

  private static void buildModulePresenceChanges(@NotNull final Context context) {
    buildPresenceChanges(context.oldModules.keySet(), context.currentModules.keySet(), MODULE_ADDED, MODULE_REMOVED, context);
  }

  private static void buildDependencyPresenceChanges(@NotNull Context context) {
    Set<String> commonModuleNames = ContainerUtilRt.newHashSet(context.currentModules.keySet());
    commonModuleNames.retainAll(context.oldModules.keySet());
    for (final String moduleName : commonModuleNames) {
      final Map<String, ModuleDependencyData> currentModuleDependencies = ContainerUtilRt.newHashMap();
      final Map<String, ModuleDependencyData> oldModuleDependencies = ContainerUtilRt.newHashMap();
      final Map<String, LibraryDependencyData> currentLibraryDependencies = ContainerUtilRt.newHashMap();
      final Map<String, LibraryDependencyData> oldLibraryDependencies = ContainerUtilRt.newHashMap();
      
      // TODO den implement
//      ExternalEntityVisitor oldStateVisitor = new ExternalEntityVisitorAdapter() {
//        @Override
//        public void visit(@NotNull ModuleDependencyData dependency) {
//          oldModuleDependencies.put(dependency.getTarget().getName(), dependency);
//        }
//
//        @Override
//        public void visit(@NotNull LibraryDependencyData dependency) {
//          oldLibraryDependencies.put(dependency.getTarget().getName(), dependency);
//        }
//      };
//      for (DependencyData dependency : context.oldModules.get(moduleName).getDependencies()) {
//        dependency.invite(oldStateVisitor);
//      }

//      ExternalEntityVisitor currentStateVisitor = new ExternalEntityVisitorAdapter() {
//        @Override
//        public void visit(@NotNull ModuleDependencyData dependency) {
//          currentModuleDependencies.put(dependency.getTarget().getName(), dependency);
//        }
//
//        @Override
//        public void visit(@NotNull LibraryDependencyData dependency) {
//          currentLibraryDependencies.put(dependency.getTarget().getName(), dependency);
//        }
//      };
//      for (DependencyData dependency : context.currentModules.get(moduleName).getDependencies()) {
//        dependency.invite(currentStateVisitor);
//      }
      
      Function<String, UserProjectChange<?>> addedModuleDependency = new Function<String, UserProjectChange<?>>() {
        @Override
        public UserProjectChange<?> fun(String s) {
          return new AddModuleDependencyUserChange(moduleName, s);
        }
      };
      Function<String, UserProjectChange<?>> removedModuleDependency = new Function<String, UserProjectChange<?>>() {
        @Override
        public UserProjectChange<?> fun(String s) {
          return new RemoveModuleDependencyUserChange(moduleName, s);
        }
      };
      Function<String, UserProjectChange<?>> addedLibraryDependency = new Function<String, UserProjectChange<?>>() {
        @Override
        public UserProjectChange<?> fun(String s) {
          return new AddLibraryDependencyUserChange(moduleName, s);
        }
      };
      Function<String, UserProjectChange<?>> removedLibraryDependency = new Function<String, UserProjectChange<?>>() {
        @Override
        public UserProjectChange<?> fun(String s) {
          return new RemoveLibraryDependencyUserChange(moduleName, s);
        }
      };
      
      buildPresenceChanges(oldModuleDependencies.keySet(), currentModuleDependencies.keySet(),
                           addedModuleDependency, removedModuleDependency, context);
      buildPresenceChanges(oldLibraryDependencies.keySet(), currentLibraryDependencies.keySet(),
                           addedLibraryDependency, removedLibraryDependency, context);

      NullableFunction<Pair<ModuleDependencyData, ModuleDependencyData>, UserProjectChange<?>> exportedModuleDependencyBuilder
        = new NullableFunction<Pair<ModuleDependencyData, ModuleDependencyData>, UserProjectChange<?>>() {
        @Nullable
        @Override
        public UserProjectChange<?> fun(Pair<ModuleDependencyData, ModuleDependencyData> pair) {
          if (pair.first.isExported() != pair.second.isExported()) {
            return new ModuleDependencyExportedChange(moduleName, pair.second.getName(), pair.second.isExported());
          }
          return null;
        }
      };
      NullableFunction<Pair<ModuleDependencyData, ModuleDependencyData>, UserProjectChange<?>> scopeModuleDependencyBuilder
        = new NullableFunction<Pair<ModuleDependencyData, ModuleDependencyData>, UserProjectChange<?>>() {
        @Nullable
        @Override
        public UserProjectChange<?> fun(Pair<ModuleDependencyData, ModuleDependencyData> pair) {
          if (pair.first.getScope() != pair.second.getScope()) {
            return new ModuleDependencyScopeUserChange(moduleName, pair.second.getName(), pair.second.getScope());
          }
          return null;
        }
      };
      NullableFunction<Pair<LibraryDependencyData, LibraryDependencyData>, UserProjectChange<?>> exportedLibDependencyBuilder
        = new NullableFunction<Pair<LibraryDependencyData, LibraryDependencyData>, UserProjectChange<?>>() {
        @Nullable
        @Override
        public UserProjectChange<?> fun(Pair<LibraryDependencyData, LibraryDependencyData> pair) {
          if (pair.first.isExported() != pair.second.isExported()) {
            return new LibraryDependencyExportedChange(moduleName, pair.second.getName(), pair.second.isExported());
          }
          return null;
        }
      };
      NullableFunction<Pair<LibraryDependencyData, LibraryDependencyData>, UserProjectChange<?>> scopeLibDependencyBuilder
        = new NullableFunction<Pair<LibraryDependencyData, LibraryDependencyData>, UserProjectChange<?>>() {
        @Nullable
        @Override
        public UserProjectChange<?> fun(Pair<LibraryDependencyData, LibraryDependencyData> pair) {
          if (pair.first.getScope() != pair.second.getScope()) {
            return new LibraryDependencyScopeUserChange(moduleName, pair.second.getName(), pair.second.getScope());
          }
          return null;
        }
      };
      
      buildSettingsChanges(oldModuleDependencies, currentModuleDependencies, exportedModuleDependencyBuilder, context);
      buildSettingsChanges(oldModuleDependencies, currentModuleDependencies, scopeModuleDependencyBuilder, context);
      buildSettingsChanges(oldLibraryDependencies, currentLibraryDependencies, exportedLibDependencyBuilder, context);
      buildSettingsChanges(oldLibraryDependencies, currentLibraryDependencies, scopeLibDependencyBuilder, context);
    }
  }
  
  private static <T> void buildPresenceChanges(@NotNull Set<T> oldData,
                                               @NotNull Set<T> currentData,
                                               @NotNull Function<T, UserProjectChange<?>> addChangeBuilder,
                                               @NotNull Function<T, UserProjectChange<?>> removeChangeBuilder,
                                               @NotNull Context context)
  {
    Set<T> removed = ContainerUtilRt.newHashSet(oldData);
    removed.removeAll(currentData);
    if (!removed.isEmpty()) {
      for (final T r : removed) {
        context.currentChanges.add(removeChangeBuilder.fun(r));
      }
    }

    Set<T> added = ContainerUtilRt.newHashSet(currentData);
    added.removeAll(oldData);
    if (!added.isEmpty()) {
      for (final T a : added) {
        context.currentChanges.add(addChangeBuilder.fun(a));
      }
    }
  }
  
  private static <T> void buildSettingsChanges(@NotNull Map<String, T> oldData,
                                               @NotNull Map<String, T> currentData,
                                               @NotNull NullableFunction<Pair<T, T>, UserProjectChange<?>> builder,
                                               @NotNull Context context)
  {
    Set<String> keys = ContainerUtilRt.newHashSet(oldData.keySet());
    keys.retainAll(currentData.keySet());
    for (String key : keys) {
      UserProjectChange<?> change = builder.fun(Pair.create(oldData.get(key), currentData.get(key)));
      if (change != null) {
        context.currentChanges.add(change);
      }
    }
  }
  
  @Nullable
  private ProjectData buildCurrentIdeProject(@NotNull Project project) {
    String compileOutput = null;
    CompilerProjectExtension compilerProjectExtension = CompilerProjectExtension.getInstance(project);
    if (compilerProjectExtension != null) {
      compileOutput = compilerProjectExtension.getCompilerOutputUrl();
    }
    if (compileOutput == null) {
      compileOutput = "";
    }
    
    return null;
    
    // TODO den implement
//    ProjectData result = new ProjectData(ProjectSystemId.IDE, ".", compileOutput);
//    final Map<String, ModuleData> modules = ContainerUtilRt.newHashMap();
//    for (Module ideModule : myFacade.getModules(project)) {
//      final ModuleData module = new ModuleData(ProjectSystemId.IDE, ideModule.getName(), ideModule.getModuleFilePath());
//      modules.put(module.getName(), module);
//    }
//    for (Module ideModule : myFacade.getModules(project)) {
//      final ModuleData module = modules.get(ideModule.getName());
//      RootPolicy<Void> visitor = new RootPolicy<Void>() {
//        @Override
//        public Void visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Void value) {
//          Library library = libraryOrderEntry.getLibrary();
//          if (library != null) {
//            LibraryDependencyData dependency = new LibraryDependencyData(
//              module,
//              new LibraryData(ProjectSystemId.IDE, ExternalSystemUtil.getLibraryName(library))
//            );
//            dependency.setScope(libraryOrderEntry.getScope());
//            dependency.setExported(libraryOrderEntry.isExported());
//            module.addDependency(dependency);
//          }
//          return value;
//        }
//
//        @Override
//        public Void visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Void value) {
//          ModuleData dependencyModule = modules.get(moduleOrderEntry.getModuleName());
//          if (dependencyModule != null) {
//            ModuleDependencyData dependency = new ModuleDependencyData(module, dependencyModule);
//            dependency.setScope(moduleOrderEntry.getScope());
//            dependency.setExported(moduleOrderEntry.isExported());
//            module.addDependency(dependency);
//          }
//          return value;
//        }
//      };
//      for (OrderEntry orderEntry : myFacade.getOrderEntries(ideModule)) {
//        orderEntry.accept(visitor, null);
//      }
//      result.addModule(module);
//    }
//    
//    return result;
  }

  /**
   * This method allows to answer if given change is up to date.
   * 
   * @param change  change to check
   * @param project target ide project
   * @return        <code>true</code> if given change is up to date; <code>false</code> otherwise
   * @see #filterOutdatedChanges(Project) 
   */
  private boolean isUpToDate(@NotNull UserProjectChange<?> change, @NotNull final Project project) {
    final Ref<Boolean> result = new Ref<Boolean>();
    change.invite(new UserProjectChangeVisitor() {
      @Override
      public void visit(@NotNull AddModuleUserChange change) {
        String moduleName = change.getModuleName();
        assert moduleName != null;
        result.set(myProjectStructureHelper.findIdeModule(moduleName, project) != null);
      }

      @Override
      public void visit(@NotNull RemoveModuleUserChange change) {
        String moduleName = change.getModuleName();
        assert moduleName != null;
        result.set(myProjectStructureHelper.findIdeModule(moduleName, project) == null); 
      }

      @Override
      public void visit(@NotNull AddModuleDependencyUserChange change) {
        String moduleName = change.getModuleName();
        assert moduleName != null;
        String dependencyName = change.getDependencyName();
        assert dependencyName != null;
        result.set(myProjectStructureHelper.findIdeModuleDependency(moduleName, dependencyName, project) != null);
      }

      @Override
      public void visit(@NotNull RemoveModuleDependencyUserChange change) {
        String moduleName = change.getModuleName();
        assert moduleName != null;
        String dependencyName = change.getDependencyName();
        assert dependencyName != null;
        result.set(myProjectStructureHelper.findIdeModuleDependency(moduleName, dependencyName, project) == null); 
      }

      @Override
      public void visit(@NotNull AddLibraryDependencyUserChange change) {
        String moduleName = change.getModuleName();
        assert moduleName != null;
        String dependencyName = change.getDependencyName();
        assert dependencyName != null;
        result.set(myProjectStructureHelper.findIdeLibraryDependency(moduleName, dependencyName, project) != null); 
      }

      @Override
      public void visit(@NotNull RemoveLibraryDependencyUserChange change) {
        String moduleName = change.getModuleName();
        assert moduleName != null;
        String dependencyName = change.getDependencyName();
        assert dependencyName != null;
        result.set(myProjectStructureHelper.findIdeLibraryDependency(moduleName, dependencyName, project) == null); 
      }

      @Override
      public void visit(@NotNull LibraryDependencyScopeUserChange change) {
        String moduleName = change.getModuleName();
        assert moduleName != null;
        String dependencyName = change.getDependencyName();
        assert dependencyName != null;
        ExportableOrderEntry dependency = myProjectStructureHelper.findIdeLibraryDependency(moduleName, dependencyName, project);
        result.set(dependency != null && dependency.getScope() == change.getScope());
      }

      @Override
      public void visit(@NotNull ModuleDependencyScopeUserChange change) {
        String moduleName = change.getModuleName();
        assert moduleName != null;
        String dependencyName = change.getDependencyName();
        assert dependencyName != null;
        ExportableOrderEntry dependency = myProjectStructureHelper.findIdeModuleDependency(moduleName, dependencyName, project);
        result.set(dependency != null && dependency.getScope() == change.getScope()); 
      }

      @Override
      public void visit(@NotNull LibraryDependencyExportedChange change) {
        String moduleName = change.getModuleName();
        assert moduleName != null;
        String dependencyName = change.getDependencyName();
        assert dependencyName != null;
        ExportableOrderEntry dependency = myProjectStructureHelper.findIdeLibraryDependency(moduleName, dependencyName, project);
        result.set(dependency != null && dependency.isExported() == change.isExported()); 
      }

      @Override
      public void visit(@NotNull ModuleDependencyExportedChange change) {
        String moduleName = change.getModuleName();
        assert moduleName != null;
        String dependencyName = change.getDependencyName();
        assert dependencyName != null;
        ExportableOrderEntry dependency = myProjectStructureHelper.findIdeModuleDependency(moduleName, dependencyName, project);
        result.set(dependency != null && dependency.isExported() == change.isExported()); 
      }
    });
    return result.get();
  }
  
  private static class Context {

    @NotNull public final Set<UserProjectChange<?>> currentChanges = ContainerUtilRt.newHashSet();

    @NotNull public final Map<String, ModuleData> oldModules;
    @NotNull public final Map<String, ModuleData> currentModules;

    Context(@NotNull ProjectData oldProjectState,
            @NotNull ProjectData currentProjectState)
    {
      Function<ModuleData, Pair<String, ModuleData>> modulesByName
        = new Function<ModuleData, Pair<String, ModuleData>>() {
        @Override
        public Pair<String, ModuleData> fun(ModuleData module) {
          return Pair.create(module.getName(), module);
        }
      };

      // TODO den remove
      oldModules = ContainerUtil.newHashMap();
      currentModules = ContainerUtil.newHashMap();
      // TODO den uncomment
//      oldModules = ContainerUtil.map2Map(oldProjectState.getModules(), modulesByName);
//      currentModules = ContainerUtil.map2Map(currentProjectState.getModules(), modulesByName);
    }
  }
}
