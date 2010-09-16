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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class LibrariesContainerFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory");
  private static final Library[] EMPTY_LIBRARIES_ARRAY = new Library[0];

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

  @NotNull
  public static LibrariesContainer createContainer() {
    return new LibrariesContainerImpl(null, null, null);
  }

  public static LibrariesContainer createContainer(@NotNull Project project, StructureConfigurableContext context) {
    return new StructureConfigurableLibrariesContainer(project, context);
  }

  public static Library createLibrary(@Nullable LibrariesContainer container1, @NotNull LibrariesContainer container2,
                               @NotNull @NonNls final String name, @NotNull final LibrariesContainer.LibraryLevel level,
                               @NotNull final VirtualFile[] classRoots, @NotNull final VirtualFile[] sourceRoots) {
    if (container1 != null && container1.canCreateLibrary(level)) {
      return container1.createLibrary(name, level, classRoots, sourceRoots);
    }
    else {
      return container2.createLibrary(name, level, classRoots, sourceRoots);
    }
  }

  @NotNull
  public static Library createLibraryInTable(final @NonNls String name, final VirtualFile[] roots, final VirtualFile[] sources, final LibraryTable table) {
    LibraryTable.ModifiableModel modifiableModel = table.getModifiableModel();
    Library library = modifiableModel.createLibrary(getUniqueLibraryName(name, modifiableModel));
    final Library.ModifiableModel model = library.getModifiableModel();
    for (VirtualFile root : roots) {
      model.addRoot(root, OrderRootType.CLASSES);
    }
    for (VirtualFile root : sources) {
      model.addRoot(root, OrderRootType.SOURCES);
    }
    model.commit();
    modifiableModel.commit();
    return library;
  }

  private static String getUniqueLibraryName(final String baseName, final LibraryTable.ModifiableModel model) {
    String name = baseName;
    int count = 2;
    while (model.getLibraryByName(name) != null) {
      name = baseName + " (" + count++ + ")";
    }
    return name;
  }


  private abstract static class LibrariesContainerBase implements LibrariesContainer {
    @NotNull
    public Library[] getAllLibraries() {
      Library[] libraries = getLibraries(LibraryLevel.GLOBAL);
      Library[] projectLibraries = getLibraries(LibraryLevel.PROJECT);
      if (projectLibraries.length > 0) {
        libraries = ArrayUtil.mergeArrays(libraries, projectLibraries, Library.class);
      }
      Library[] moduleLibraries = getLibraries(LibraryLevel.MODULE);
      if (moduleLibraries.length > 0) {
        libraries = ArrayUtil.mergeArrays(libraries, moduleLibraries, Library.class);
      }
      return libraries;
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

    @Nullable
    public Project getProject() {
      return myProject;
    }

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
      OrderEntry[] orderEntries = ModuleRootManager.getInstance(myModule).getOrderEntries();
      List<Library> libraries = new ArrayList<Library>();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry) {
          final LibraryOrderEntry entry = (LibraryOrderEntry)orderEntry;
          if (entry.isModuleLevel()) {
            libraries.add(entry.getLibrary());
          }
        }
      }
      return libraries.toArray(new Library[libraries.size()]);
    }

    @NotNull
    public VirtualFile[] getLibraryFiles(@NotNull final Library library, @NotNull final OrderRootType rootType) {
      return library.getFiles(rootType);
    }

    public boolean canCreateLibrary(@NotNull final LibraryLevel level) {
      if (level == LibraryLevel.MODULE) {
        return myRootModel != null;
      }
      return level == LibraryLevel.GLOBAL || myProject != null;
    }

    public Library createLibrary(@NotNull @NonNls final String name, @NotNull final LibraryLevel level,
                                 @NotNull final VirtualFile[] classRoots, @NotNull final VirtualFile[] sourceRoots) {
      if (level == LibraryLevel.MODULE && myRootModel != null) {
        return createLibraryInTable(name, classRoots, sourceRoots, myRootModel.getModuleLibraryTable());
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
      return createLibraryInTable(name, classRoots, sourceRoots, table);
    }
  }

  private static class StructureConfigurableLibrariesContainer extends LibrariesContainerBase {
    private final Project myProject;
    private final StructureConfigurableContext myContext;

    public StructureConfigurableLibrariesContainer(final Project project, final StructureConfigurableContext context) {
      myProject = project;
      myContext = context;
    }

    public Library createLibrary(@NotNull @NonNls final String name, @NotNull final LibraryLevel level,
                                 @NotNull final VirtualFile[] classRoots, @NotNull final VirtualFile[] sourceRoots) {
      LibraryTableModifiableModelProvider provider = getProvider(level);
      if (provider == null) {
        LOG.error("cannot create module library in this context");
      }

      LibraryTable.ModifiableModel model = provider.getModifiableModel();
      Library library = model.createLibrary(getUniqueLibraryName(name, model));
      LibraryEditor libraryEditor = ((LibrariesModifiableModel)model).getLibraryEditor(library);
      for (VirtualFile root : classRoots) {
        libraryEditor.addRoot(root, OrderRootType.CLASSES);
      }
      for (VirtualFile source : sourceRoots) {
        libraryEditor.addRoot(source, OrderRootType.SOURCES);
      }
      return library;
    }

    @Nullable
    public Project getProject() {
      return myProject;
    }

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

    public boolean canCreateLibrary(@NotNull final LibraryLevel level) {
      return level == LibraryLevel.GLOBAL || level == LibraryLevel.PROJECT;
    }

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
