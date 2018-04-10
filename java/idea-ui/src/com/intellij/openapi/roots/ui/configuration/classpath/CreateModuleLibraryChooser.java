/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.*;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.libraries.ui.impl.RootDetectionUtil;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.DefaultLibraryRootsComponentDescriptor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.util.ArrayUtil.contains;

/**
* @author nik
*/
public class CreateModuleLibraryChooser implements ClasspathElementChooser<Library> {
  private final JComponent myParentComponent;
  private final Module myModule;
  private final LibraryTable.ModifiableModel myModuleLibrariesModel;
  @Nullable private final Function<LibraryType, LibraryProperties> myDefaultPropertiesFactory;
  private final HashMap<LibraryRootsComponentDescriptor,LibraryType> myLibraryTypes;
  private final DefaultLibraryRootsComponentDescriptor myDefaultDescriptor;

  public CreateModuleLibraryChooser(ClasspathPanel classpathPanel, LibraryTable.ModifiableModel moduleLibraryModel) {
    this(LibraryEditingUtil.getSuitableTypes(classpathPanel), classpathPanel.getComponent(), classpathPanel.getRootModel().getModule(),
         moduleLibraryModel, null);
  }

  public CreateModuleLibraryChooser(List<? extends LibraryType> libraryTypes, JComponent parentComponent,
                                    Module module,
                                    final LibraryTable.ModifiableModel moduleLibrariesModel,
                                    @Nullable final Function<LibraryType, LibraryProperties> defaultPropertiesFactory) {
    myParentComponent = parentComponent;
    myModule = module;
    myModuleLibrariesModel = moduleLibrariesModel;
    myDefaultPropertiesFactory = defaultPropertiesFactory;
    myLibraryTypes = new HashMap<>();
    myDefaultDescriptor = new DefaultLibraryRootsComponentDescriptor();
    for (LibraryType<?> libraryType : libraryTypes) {
      LibraryRootsComponentDescriptor descriptor = null;
      if (libraryType != null) {
        descriptor = libraryType.createLibraryRootsComponentDescriptor();
      }
      if (descriptor == null) {
        descriptor = myDefaultDescriptor;
      }
      if (!myLibraryTypes.containsKey(descriptor)) {
        myLibraryTypes.put(descriptor, libraryType);
      }
    }
  }

  private static Library createLibraryFromRoots(@NotNull List<OrderRoot> roots,
                                                @Nullable final LibraryType libraryType,
                                                @NotNull LibraryTable.ModifiableModel moduleLibrariesModel,
                                                @Nullable Function<LibraryType, LibraryProperties> defaultPropertiesFactory) {
    final PersistentLibraryKind kind = libraryType == null ? null : libraryType.getKind();
    final Library library = moduleLibrariesModel.createLibrary(null, kind);
    final LibraryEx.ModifiableModelEx libModel = (LibraryEx.ModifiableModelEx)library.getModifiableModel();
    if (defaultPropertiesFactory != null) {
      libModel.setProperties(defaultPropertiesFactory.fun(libraryType));
    }
    for (OrderRoot root : roots) {
      if (root.isJarDirectory()) {
        libModel.addJarDirectory(root.getFile(), false, root.getType());
      }
      else {
        libModel.addRoot(root.getFile(), root.getType());
      }
    }
    libModel.commit();
    return library;
  }

  private static List<OrderRoot> filterAlreadyAdded(final List<OrderRoot> roots, LibraryTable.ModifiableModel moduleLibrariesModel) {
    if (roots == null || roots.isEmpty()) {
      return Collections.emptyList();
    }

    final List<OrderRoot> result = new ArrayList<>();
    final Library[] libraries = moduleLibrariesModel.getLibraries();
    for (OrderRoot root : roots) {
      if (Arrays.stream(libraries).noneMatch(library -> contains(root.getFile(), library.getFiles(root.getType())))) {
        result.add(root);
      }
    }
    return result;
  }

  @Override
  @NotNull
  public List<Library> chooseElements() {
    final FileChooserDescriptor chooserDescriptor;
    final List<Pair<LibraryRootsComponentDescriptor, FileChooserDescriptor>> descriptors = new ArrayList<>();
    for (LibraryRootsComponentDescriptor componentDescriptor : myLibraryTypes.keySet()) {
      descriptors.add(Pair.create(componentDescriptor, componentDescriptor.createAttachFilesChooserDescriptor(null)));
    }
    if (descriptors.size() == 1) {
      chooserDescriptor = descriptors.get(0).getSecond();
    }
    else {
      chooserDescriptor = new FileChooserDescriptor(true, true, true, false, true, false) {
        @Override
        public boolean isFileSelectable(VirtualFile file) {
          for (Pair<LibraryRootsComponentDescriptor, FileChooserDescriptor> pair : descriptors) {
            if (pair.getSecond().isFileSelectable(file)) {
              return true;
            }
          }
          return false;
        }

        @Override
        public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
          for (Pair<LibraryRootsComponentDescriptor, FileChooserDescriptor> pair : descriptors) {
            if (pair.getSecond().isFileVisible(file, showHiddenFiles)) {
              return true;
            }
          }
          return false;
        }
      };
    }
    chooserDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, myModule);

    final Project project = myModule.getProject();
    final VirtualFile[] files = FileChooser.chooseFiles(chooserDescriptor, myParentComponent, project, project.getBaseDir());
    if (files.length == 0) return Collections.emptyList();

    List<LibraryRootsComponentDescriptor> suitableDescriptors = new ArrayList<>();
    for (Pair<LibraryRootsComponentDescriptor, FileChooserDescriptor> pair : descriptors) {
      if (acceptAll(pair.getSecond(), files)) {
        suitableDescriptors.add(pair.getFirst());
      }
    }

    final LibraryRootsComponentDescriptor rootsComponentDescriptor;
    LibraryType libraryType = null;
    if (suitableDescriptors.size() == 1) {
      rootsComponentDescriptor = suitableDescriptors.get(0);
      libraryType = myLibraryTypes.get(rootsComponentDescriptor);
    }
    else {
      rootsComponentDescriptor = myDefaultDescriptor;
    }
    List<OrderRoot> chosenRoots = RootDetectionUtil.detectRoots(Arrays.asList(files), myParentComponent, project, rootsComponentDescriptor);

    return createLibrariesFromRoots(chosenRoots, libraryType, myModuleLibrariesModel, myDefaultPropertiesFactory);
  }

  @TestOnly
  @NotNull
  public static List<Library> createLibrariesFromRoots(List<OrderRoot> chosenRoots, LibraryTable.ModifiableModel moduleLibrariesModel) {
    return createLibrariesFromRoots(chosenRoots, null, moduleLibrariesModel, null);
  }

  @NotNull
  private static List<Library> createLibrariesFromRoots(@NotNull List<OrderRoot> chosenRoots,
                                                        @Nullable LibraryType libraryType,
                                                        @NotNull LibraryTable.ModifiableModel moduleLibrariesModel,
                                                        @Nullable Function<LibraryType, LibraryProperties> defaultPropertiesFactory) {
    final List<OrderRoot> roots = filterAlreadyAdded(chosenRoots, moduleLibrariesModel);
    if (roots.isEmpty()) {
      return Collections.emptyList();
    }

    final List<Library> addedLibraries = new ArrayList<>();
    Map<VirtualFile, List<OrderRoot>> byFile = roots.stream().collect(Collectors.groupingBy(OrderRoot::getFile, LinkedHashMap::new, Collectors.toList()));
    Predicate<List<OrderRoot>> containsClasses = it -> it.stream().anyMatch(root -> root.getType().equals(OrderRootType.CLASSES));
    if (byFile.values().stream().allMatch(containsClasses)) {
      for (List<OrderRoot> rootsForFile : byFile.values()) {
        addedLibraries.add(createLibraryFromRoots(rootsForFile, libraryType, moduleLibrariesModel, defaultPropertiesFactory));
      }
    }
    else {
      addedLibraries.add(createLibraryFromRoots(roots, libraryType, moduleLibrariesModel, defaultPropertiesFactory));
    }
    return addedLibraries;
  }

  private static boolean acceptAll(FileChooserDescriptor descriptor, VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (!descriptor.isFileSelectable(file) || !descriptor.isFileVisible(file, true)) {
        return false;
      }
    }
    return true;
  }
}
