// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CreateModuleLibraryChooser implements ClasspathElementChooser<Library> {
  private final JComponent myParentComponent;
  private final Module myModule;
  private final LibraryTable.ModifiableModel myModuleLibrariesModel;
  private final @Nullable Function<? super LibraryType, ? extends LibraryProperties> myDefaultPropertiesFactory;
  private final HashMap<LibraryRootsComponentDescriptor,LibraryType> myLibraryTypes;
  private final DefaultLibraryRootsComponentDescriptor myDefaultDescriptor;

  public CreateModuleLibraryChooser(ClasspathPanel classpathPanel, LibraryTable.ModifiableModel moduleLibraryModel) {
    this(ContainerUtil.map(LibraryEditingUtil.getSuitableTypes(classpathPanel), LibraryEditingUtil.TypeForNewLibrary::getType), classpathPanel.getComponent(), classpathPanel.getRootModel().getModule(),
                           moduleLibraryModel, null);
  }

  public CreateModuleLibraryChooser(List<? extends LibraryType> libraryTypes, JComponent parentComponent,
                                    Module module,
                                    final LibraryTable.ModifiableModel moduleLibrariesModel,
                                    final @Nullable Function<? super LibraryType, ? extends LibraryProperties> defaultPropertiesFactory) {
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
      boolean acceptsClasses = descriptor.getRootDetectors().stream().anyMatch(detector -> detector.getRootType().equals(OrderRootType.CLASSES));
      if (acceptsClasses && !myLibraryTypes.containsKey(descriptor)) {
        myLibraryTypes.put(descriptor, libraryType);
      }
    }
  }

  private static @NotNull Library createLibraryFromRoots(@NotNull List<OrderRoot> roots,
                                                         @Nullable LibraryType libraryType,
                                                         @NotNull LibraryTable.ModifiableModel moduleLibrariesModel,
                                                         @Nullable Function<? super LibraryType, ? extends LibraryProperties> defaultPropertiesFactory) {
    PersistentLibraryKind kind = libraryType == null ? null : libraryType.getKind();
    Library library = moduleLibrariesModel.createLibrary(null, kind);
    LibraryEx.ModifiableModelEx libModel = (LibraryEx.ModifiableModelEx)library.getModifiableModel();
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

  @SuppressWarnings("SSBasedInspection")
  private static @NotNull List<OrderRoot> filterAlreadyAdded(List<OrderRoot> roots, LibraryTable.ModifiableModel moduleLibrariesModel) {
    if (roots == null || roots.isEmpty()) {
      return Collections.emptyList();
    }

    Library[] libraries = moduleLibrariesModel.getLibraries();
    if (libraries.length == 0) {
      return roots;
    }

    return roots.stream()
      .filter(root -> {
        return Arrays.stream(libraries).noneMatch(library -> ArrayUtil.contains(root.getFile(), library.getFiles(root.getType())));
      })
      .collect(Collectors.toList());
  }

  @Override
  public @NotNull List<Library> chooseElements() {
    FileChooserDescriptor chooserDescriptor;
    List<Pair<LibraryRootsComponentDescriptor, FileChooserDescriptor>> descriptors = new ArrayList<>();
    for (LibraryRootsComponentDescriptor componentDescriptor : myLibraryTypes.keySet()) {
      descriptors.add(Pair.create(componentDescriptor, componentDescriptor.createAttachFilesChooserDescriptor(null)));
    }
    if (descriptors.size() == 1) {
      chooserDescriptor = descriptors.get(0).getSecond();
    }
    else {
      chooserDescriptor = new FileChooserDescriptor(true, true, true, false, true, false) {
        @Override
        public boolean isFileSelectable(@Nullable VirtualFile file) {
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

    Project project = myModule.getProject();
    VirtualFile[] files = FileChooser.chooseFiles(chooserDescriptor, myParentComponent, project, project.getBaseDir());
    if (files.length == 0) {
      return Collections.emptyList();
    }

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
  public static @NotNull List<Library> createLibrariesFromRoots(List<OrderRoot> chosenRoots, LibraryTable.ModifiableModel moduleLibrariesModel) {
    return createLibrariesFromRoots(chosenRoots, null, moduleLibrariesModel, null);
  }

  private static @NotNull List<Library> createLibrariesFromRoots(@NotNull List<OrderRoot> chosenRoots,
                                                                 @Nullable LibraryType libraryType,
                                                                 @NotNull LibraryTable.ModifiableModel moduleLibrariesModel,
                                                                 @Nullable Function<? super LibraryType, ? extends LibraryProperties> defaultPropertiesFactory) {
    List<OrderRoot> roots = filterAlreadyAdded(chosenRoots, moduleLibrariesModel);
    if (roots.isEmpty()) {
      return Collections.emptyList();
    }

    Map<VirtualFile, List<OrderRoot>> byFile = roots.stream().collect(Collectors.groupingBy(OrderRoot::getFile, LinkedHashMap::new, Collectors.toList()));
    Predicate<List<OrderRoot>> containsClasses = it -> it.stream().anyMatch(root -> root.getType().equals(OrderRootType.CLASSES));
    //noinspection SSBasedInspection
    if (byFile.values().stream().allMatch(containsClasses)) {
      List<Library> addedLibraries = new ArrayList<>();
      for (List<OrderRoot> rootsForFile : byFile.values()) {
        addedLibraries.add(createLibraryFromRoots(rootsForFile, libraryType, moduleLibrariesModel, defaultPropertiesFactory));
      }
      return addedLibraries;
    }
    else {
      return List.of(createLibraryFromRoots(roots, libraryType, moduleLibrariesModel, defaultPropertiesFactory));
    }
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