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

  public void dispose() {
  }

  public ModulesConfigurator getModulesConfigurator() {
    return myModulesConfigurator;
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