// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.Disposable;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class StructureConfigurableContext implements Disposable, LibraryEditorListener {
  private final ProjectStructureDaemonAnalyzer myDaemonAnalyzer;
  public final ModulesConfigurator myModulesConfigurator;
  public final Map<String, LibrariesModifiableModel> myLevel2Providers = new HashMap<>();
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
    if (table != null &&
        getModifiableLibraryTable(table) instanceof LibrariesModifiableModel librariesModel &&
        librariesModel.hasLibraryEditor(library)) {
      return librariesModel.getLibraryEditor(library).getFiles(type);
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
    return myModulesConfigurator.getModuleModel().getActualName(module);
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

  public @NotNull StructureLibraryTableModifiableModelProvider getGlobalLibrariesProvider() {
    return createModifiableModelProvider(LibraryTablesRegistrar.APPLICATION_LEVEL);
  }

  public @NotNull StructureLibraryTableModifiableModelProvider createModifiableModelProvider(@NotNull String level) {
    return new StructureLibraryTableModifiableModelProvider(level, this);
  }

  public @NotNull StructureLibraryTableModifiableModelProvider getProjectLibrariesProvider() {
    return createModifiableModelProvider(LibraryTablesRegistrar.PROJECT_LEVEL);
  }


  public LibraryTable.ModifiableModel getModifiableLibraryTable(@NotNull LibraryTable table) {
    final String tableLevel = table.getTableLevel();
    if (tableLevel.equals(LibraryTableImplUtil.MODULE_LEVEL)) {
      return table.getModifiableModel();
    }
    return myLevel2Providers.get(tableLevel);
  }

  public @Nullable Library getLibrary(final String libraryName, @NotNull String libraryLevel) {
/* the null check is added only to prevent NPE when called from getLibrary */
    final LibrariesModifiableModel model = myLevel2Providers.get(libraryLevel);
    return model == null ? null : findLibraryModel(libraryName, model);
  }

  private static @Nullable Library findLibraryModel(final @NotNull String libraryName, @NotNull LibrariesModifiableModel model) {
    for (Library library : model.getLibraries()) {
      final Library libraryModel = findLibraryModel(library, model);
      if (libraryModel != null && libraryName.equals(libraryModel.getName())) {
        return libraryModel;
      }
    }
    return null;
  }

  public @Nullable Library getLibraryModel(@NotNull Library library) {
    final LibraryTable libraryTable = library.getTable();
    if (libraryTable != null) {
      return findLibraryModel(library, myLevel2Providers.get(libraryTable.getTableLevel()));
    }
    return library;
  }

  private static @Nullable Library findLibraryModel(final Library library, LibrariesModifiableModel tableModel) {
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
