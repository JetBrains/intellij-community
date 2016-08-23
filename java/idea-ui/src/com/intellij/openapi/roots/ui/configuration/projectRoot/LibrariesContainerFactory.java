/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class LibrariesContainerFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory");
  private static final Library[] EMPTY_LIBRARIES_ARRAY = Library.EMPTY_ARRAY;

  private LibrariesContainerFactory() {
  }

  @NotNull
  public static LibrariesContainer createContainer(@Nullable Project project) {
    return new LibrariesContainerImpl(project, null, null);
  }

  @NotNull
  public static LibrariesContainer createContainer(@NotNull Module module) {
    return new LibrariesContainerImpl(module.getProject(), module, null);
  }

  @NotNull
  public static LibrariesContainer createContainer(@NotNull ModifiableRootModel rootModel) {
    Module module = rootModel.getModule();
    return new LibrariesContainerImpl(module.getProject(), module, rootModel);
  }

  public static LibrariesContainer createContainer(StructureConfigurableContext context) {
    return new StructureConfigurableLibrariesContainer(context);
  }

  public static Library createLibrary(@Nullable LibrariesContainer container1, @NotNull LibrariesContainer container2,
                               @NotNull @NonNls final NewLibraryEditor editor, @NotNull final LibrariesContainer.LibraryLevel level) {
    if (container1 != null && container1.canCreateLibrary(level)) {
      return container1.createLibrary(editor, level);
    }
    else {
      return container2.createLibrary(editor, level);
    }
  }

  @NotNull
  private static Library createLibraryInTable(final @NotNull NewLibraryEditor editor, final LibraryTable table) {
    LibraryTable.ModifiableModel modifiableModel = table.getModifiableModel();
    final String name = StringUtil.isEmpty(editor.getName()) ? null : getUniqueLibraryName(editor.getName(), modifiableModel);
    final LibraryType<?> type = editor.getType();
    Library library = modifiableModel.createLibrary(name, type == null ? null : type.getKind());
    final LibraryEx.ModifiableModelEx model = (LibraryEx.ModifiableModelEx)library.getModifiableModel();
    editor.applyTo(model);
    model.commit();
    modifiableModel.commit();
    return library;
  }

  private static String getUniqueLibraryName(final String baseName, final LibraryTable.ModifiableModel model) {
    return UniqueNameGenerator.generateUniqueName(baseName, "", "", " (", ")", s -> model.getLibraryByName(s) == null);
  }

  @NotNull
  public static LibrariesContainer createContainer(@NotNull WizardContext context, @NotNull ModulesProvider modulesProvider) {
    final LibrariesContainer container;
    if (modulesProvider instanceof ModulesConfigurator) {
      ModulesConfigurator configurator = (ModulesConfigurator)modulesProvider;
      container = createContainer(configurator.getContext());
    }
    else {
      container = createContainer(context.getProject());
    }
    return container;
  }


  private abstract static class LibrariesContainerBase implements LibrariesContainer {
    private UniqueNameGenerator myNameGenerator;

    @Override
    public Library createLibrary(@NotNull @NonNls String name,
                                 @NotNull LibraryLevel level,
                                 @NotNull VirtualFile[] classRoots,
                                 @NotNull VirtualFile[] sourceRoots) {
      NewLibraryEditor editor = new NewLibraryEditor();
      editor.setName(name);
      for (VirtualFile classRoot : classRoots) {
        editor.addRoot(classRoot, OrderRootType.CLASSES);
      }
      for (VirtualFile sourceRoot : sourceRoots) {
        editor.addRoot(sourceRoot, OrderRootType.SOURCES);
      }
      return createLibrary(editor, level);
    }

    @Override
    public Library createLibrary(@NotNull @NonNls String name,
                                 @NotNull LibraryLevel level,
                                 @NotNull Collection<? extends OrderRoot> roots) {
      final NewLibraryEditor editor = new NewLibraryEditor();
      editor.setName(name);
      editor.addRoots(roots);
      return createLibrary(editor, level);
    }

    @Override
    @NotNull
    public Library[] getAllLibraries() {
      Library[] libraries = getLibraries(LibraryLevel.GLOBAL);
      Library[] projectLibraries = getLibraries(LibraryLevel.PROJECT);
      if (projectLibraries.length > 0) {
        libraries = ArrayUtil.mergeArrays(libraries, projectLibraries);
      }
      Library[] moduleLibraries = getLibraries(LibraryLevel.MODULE);
      if (moduleLibraries.length > 0) {
        libraries = ArrayUtil.mergeArrays(libraries, moduleLibraries);
      }
      return libraries;
    }

    @NotNull
    @Override
    public List<LibraryLevel> getAvailableLevels() {
      final List<LibraryLevel> levels = new ArrayList<>();
      for (LibraryLevel level : LibraryLevel.values()) {
        if (canCreateLibrary(level)) {
          levels.add(level);
        }
      }
      return levels;
    }

    @NotNull
    @Override
    public String suggestUniqueLibraryName(@NotNull String baseName) {
      if (myNameGenerator == null) {
        myNameGenerator = new UniqueNameGenerator(Arrays.asList(getAllLibraries()), o -> o.getName());
      }
      return myNameGenerator.generateUniqueName(baseName, "", "", " (", ")");
    }
  }


  private static class LibrariesContainerImpl extends LibrariesContainerBase {
    private @Nullable final Project myProject;
    @Nullable private final Module myModule;
    @Nullable private final ModifiableRootModel myRootModel;

    private LibrariesContainerImpl(final @Nullable Project project, final @Nullable Module module, final @Nullable ModifiableRootModel rootModel) {
      myProject = project;
      myModule = module;
      myRootModel = rootModel;
    }

    @Override
    @Nullable
    public Project getProject() {
      return myProject;
    }

    @Override
    @NotNull
    public Library[] getLibraries(@NotNull final LibraryLevel libraryLevel) {
      if (libraryLevel == LibraryLevel.MODULE && myModule != null) {
        return getModuleLibraries();
      }

      LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
      if (libraryLevel == LibraryLevel.GLOBAL) {
        return registrar.getLibraryTable().getLibraries();
      }

      if (libraryLevel == LibraryLevel.PROJECT && myProject != null) {
        return registrar.getLibraryTable(myProject).getLibraries();
      }

      return EMPTY_LIBRARIES_ARRAY;
    }

    private Library[] getModuleLibraries() {
      if (myRootModel != null) {
        return myRootModel.getModuleLibraryTable().getLibraries();
      }
      List<Library> libraries = OrderEntryUtil.getModuleLibraries(ModuleRootManager.getInstance(myModule));
      return libraries.toArray(new Library[libraries.size()]);
    }

    @Override
    @NotNull
    public VirtualFile[] getLibraryFiles(@NotNull final Library library, @NotNull final OrderRootType rootType) {
      return library.getFiles(rootType);
    }

    @Override
    public boolean canCreateLibrary(@NotNull final LibraryLevel level) {
      if (level == LibraryLevel.MODULE) {
        return myRootModel != null;
      }
      return level == LibraryLevel.GLOBAL || myProject != null;
    }

    @Override
    public Library createLibrary(@NotNull NewLibraryEditor libraryEditor,
                                 @NotNull LibraryLevel level) {
      if (level == LibraryLevel.MODULE && myRootModel != null) {
        return createLibraryInTable(libraryEditor, myRootModel.getModuleLibraryTable());
      }

      LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
      LibraryTable table;
      if (level == LibraryLevel.GLOBAL) {
        table = registrar.getLibraryTable();
      }
      else if (level == LibraryLevel.PROJECT && myProject != null) {
        table = registrar.getLibraryTable(myProject);
      }
      else {
        return null;
      }
      return createLibraryInTable(libraryEditor, table);
    }

    @Override
    public ExistingLibraryEditor getLibraryEditor(@NotNull Library library) {
      return null;
    }
  }

  private static class StructureConfigurableLibrariesContainer extends LibrariesContainerBase {
    private final StructureConfigurableContext myContext;

    public StructureConfigurableLibrariesContainer(final StructureConfigurableContext context) {
      myContext = context;
    }

    @Override
    public Library createLibrary(@NotNull NewLibraryEditor libraryEditor,
                                 @NotNull LibraryLevel level) {
      LibraryTableModifiableModelProvider provider = getProvider(level);
      if (provider == null) {
        LOG.error("cannot create module library in this context");
      }

      LibraryTable.ModifiableModel model = provider.getModifiableModel();
      final LibraryType<?> type = libraryEditor.getType();
      Library library = model.createLibrary(getUniqueLibraryName(libraryEditor.getName(), model), type == null ? null : type.getKind());
      ExistingLibraryEditor createdLibraryEditor = ((LibrariesModifiableModel)model).getLibraryEditor(library);
      createdLibraryEditor.setProperties(libraryEditor.getProperties());
      libraryEditor.applyTo(createdLibraryEditor);
      return library;
    }

    @Override
    public ExistingLibraryEditor getLibraryEditor(@NotNull Library library) {
      final LibraryTable table = library.getTable();
      if (table == null) return null;

      final LibraryTable.ModifiableModel model = myContext.getModifiableLibraryTable(table);
      if (model instanceof LibrariesModifiableModel) {
        return ((LibrariesModifiableModel)model).getLibraryEditor(library);
      }
      return null;
    }

    @Override
    @Nullable
    public Project getProject() {
      return myContext.getProject();
    }

    @Override
    @NotNull
    public Library[] getLibraries(@NotNull final LibraryLevel libraryLevel) {
      LibraryTableModifiableModelProvider provider = getProvider(libraryLevel);
      return provider != null ? provider.getModifiableModel().getLibraries() : EMPTY_LIBRARIES_ARRAY;
    }

    @Nullable
    private LibraryTableModifiableModelProvider getProvider(LibraryLevel libraryLevel) {
      if (libraryLevel == LibraryLevel.PROJECT) {
        return myContext.getProjectLibrariesProvider();
      }
      else if (libraryLevel == LibraryLevel.GLOBAL) {
        return myContext.getGlobalLibrariesProvider();
      }
      else {
        return null;
      }
    }

    @Override
    public boolean canCreateLibrary(@NotNull final LibraryLevel level) {
      return level == LibraryLevel.GLOBAL || level == LibraryLevel.PROJECT;
    }

    @Override
    @NotNull
    public VirtualFile[] getLibraryFiles(@NotNull final Library library, @NotNull final OrderRootType rootType) {
      LibrariesModifiableModel projectLibrariesModel = myContext.getProjectLibrariesProvider().getModifiableModel();
      if (projectLibrariesModel.hasLibraryEditor(library)) {
        LibraryEditor libraryEditor = projectLibrariesModel.getLibraryEditor(library);
        return libraryEditor.getFiles(rootType);
      }
      LibrariesModifiableModel globalLibraries = myContext.getGlobalLibrariesProvider().getModifiableModel();
      if (globalLibraries.hasLibraryEditor(library)) {
        LibraryEditor libraryEditor = globalLibraries.getLibraryEditor(library);
        return libraryEditor.getFiles(rootType);
      }
      return library.getFiles(rootType);
    }
  }
}
