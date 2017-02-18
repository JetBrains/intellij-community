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
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorListener;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureDaemonAnalyzer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class StructureConfigurableContext implements Disposable, LibraryEditorListener {
  private final ProjectStructureDaemonAnalyzer myDaemonAnalyzer;
  public final ModulesConfigurator myModulesConfigurator;
  public final Map<String, LibrariesModifiableModel> myLevel2Providers = new THashMap<>();
  private final EventDispatcher<LibraryEditorListener> myLibraryEditorListeners = EventDispatcher.create(LibraryEditorListener.class);
  private final Project myProject;


  public StructureConfigurableContext(Project project, final ModulesConfigurator modulesConfigurator) {
    myProject = project;
    myModulesConfigurator = modulesConfigurator;
    Disposer.register(project, this);
    myDaemonAnalyzer = new ProjectStructureDaemonAnalyzer(this);
  }

  public VirtualFile[] getLibraryFiles(Library library, final OrderRootType type) {
    final LibraryTable table = library.getTable();
    if (table != null) {
      final LibraryTable.ModifiableModel modifiableModel = getModifiableLibraryTable(table);
      if (modifiableModel instanceof LibrariesModifiableModel) {
        final LibrariesModifiableModel librariesModel = (LibrariesModifiableModel)modifiableModel;
        if (librariesModel.hasLibraryEditor(library)) {
          return librariesModel.getLibraryEditor(library).getFiles(type);
        }
      }
    }
    return library.getFiles(type);
  }

  public Project getProject() {
    return myProject;
  }

  public ProjectStructureDaemonAnalyzer getDaemonAnalyzer() {
    return myDaemonAnalyzer;
  }

  @Override
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
    myLevel2Providers.put(LibraryTablesRegistrar.APPLICATION_LEVEL, new LibrariesModifiableModel(tablesRegistrar.getLibraryTable(), myProject, this));
    myLevel2Providers.put(LibraryTablesRegistrar.PROJECT_LEVEL, new LibrariesModifiableModel(tablesRegistrar.getLibraryTable(myProject), myProject, this));
    for (final LibraryTable table : tablesRegistrar.getCustomLibraryTables()) {
      myLevel2Providers.put(table.getTableLevel(), new LibrariesModifiableModel(table, myProject, this));
    }
  }

  public void addLibraryEditorListener(LibraryEditorListener listener) {
    myLibraryEditorListeners.addListener(listener);
  }

  public void addLibraryEditorListener(@NotNull LibraryEditorListener listener, @NotNull Disposable parentDisposable) {
    myLibraryEditorListeners.addListener(listener, parentDisposable);
  }

  @Override
  public void libraryRenamed(@NotNull Library library, String oldName, String newName) {
    myLibraryEditorListeners.getMulticaster().libraryRenamed(library, oldName, newName);
  }

  public StructureLibraryTableModifiableModelProvider getGlobalLibrariesProvider() {
    return createModifiableModelProvider(LibraryTablesRegistrar.APPLICATION_LEVEL);
  }

  public StructureLibraryTableModifiableModelProvider createModifiableModelProvider(final String level) {
    return new StructureLibraryTableModifiableModelProvider(level, this);
  }

  public StructureLibraryTableModifiableModelProvider getProjectLibrariesProvider() {
    return createModifiableModelProvider(LibraryTablesRegistrar.PROJECT_LEVEL);
  }


  public LibraryTable.ModifiableModel getModifiableLibraryTable(@NotNull LibraryTable table) {
    final String tableLevel = table.getTableLevel();
    if (tableLevel.equals(LibraryTableImplUtil.MODULE_LEVEL)) {
      return table.getModifiableModel();
    }
    return myLevel2Providers.get(tableLevel);
  }

  @Nullable
  public Library getLibrary(final String libraryName, final String libraryLevel) {
/* the null check is added only to prevent NPE when called from getLibrary */
    final LibrariesModifiableModel model = myLevel2Providers.get(libraryLevel);
    return model == null ? null : findLibraryModel(libraryName, model);
  }

  @Nullable
  private static Library findLibraryModel(final @NotNull String libraryName, @NotNull LibrariesModifiableModel model) {
    for (Library library : model.getLibraries()) {
      final Library libraryModel = findLibraryModel(library, model);
      if (libraryModel != null && libraryName.equals(libraryModel.getName())) {
        return libraryModel;
      }
    }
    return null;
  }

  @Nullable
  public Library getLibraryModel(@NotNull Library library) {
    final LibraryTable libraryTable = library.getTable();
    if (libraryTable != null) {
      return findLibraryModel(library, myLevel2Providers.get(libraryTable.getTableLevel()));
    }
    return library;
  }

  @Nullable
  private static Library findLibraryModel(final Library library, LibrariesModifiableModel tableModel) {
    if (tableModel == null) return library;
    if (tableModel.wasLibraryRemoved(library)) return null;
    return tableModel.hasLibraryEditor(library) ? (Library)tableModel.getLibraryEditor(library).getModel() : library;
  }


  public void reset() {
    resetLibraries();
    myModulesConfigurator.resetModuleEditors();
    myDaemonAnalyzer.reset(); // should be called after resetLibraries!
  }

  public void clear() {
    myLevel2Providers.clear();
  }

}
