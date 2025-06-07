// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.libraries.ui.impl.RootDetectionUtil;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.DefaultLibraryRootsComponentDescriptor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class CreateModuleLibraryChooser implements ClasspathElementChooser<Library> {
  private final JComponent myParentComponent;
  private final Module myModule;
  private final LibraryTable.ModifiableModel myModuleLibrariesModel;
  private final @Nullable Function<? super LibraryType<?>, ? extends LibraryProperties<?>> myDefaultPropertiesFactory;
  private final Map<LibraryRootsComponentDescriptor, LibraryType<?>> myLibraryTypes;
  private final DefaultLibraryRootsComponentDescriptor myDefaultDescriptor;

  public CreateModuleLibraryChooser(ClasspathPanel classpathPanel, LibraryTable.ModifiableModel moduleLibraryModel) {
    this(
      ContainerUtil.map(LibraryEditingUtil.getSuitableTypes(classpathPanel), LibraryEditingUtil.TypeForNewLibrary::getType),
      classpathPanel.getComponent(),
      classpathPanel.getRootModel().getModule(),
      moduleLibraryModel,
      null
    );
  }

  public CreateModuleLibraryChooser(
    List<? extends LibraryType<?>> libraryTypes,
    JComponent parentComponent,
    Module module,
    LibraryTable.ModifiableModel moduleLibrariesModel,
    @Nullable Function<? super LibraryType<?>, ? extends LibraryProperties<?>> defaultPropertiesFactory
  ) {
    myParentComponent = parentComponent;
    myModule = module;
    myModuleLibrariesModel = moduleLibrariesModel;
    myDefaultPropertiesFactory = defaultPropertiesFactory;
    myLibraryTypes = new HashMap<>();
    myDefaultDescriptor = new DefaultLibraryRootsComponentDescriptor();
    for (var libraryType : libraryTypes) {
      LibraryRootsComponentDescriptor descriptor = null;
      if (libraryType != null) {
        descriptor = libraryType.createLibraryRootsComponentDescriptor();
      }
      if (descriptor == null) {
        descriptor = myDefaultDescriptor;
      }
      @SuppressWarnings("SSBasedInspection") var acceptsClasses =
        descriptor.getRootDetectors().stream().anyMatch(detector -> detector.getRootType().equals(OrderRootType.CLASSES));
      if (acceptsClasses && !myLibraryTypes.containsKey(descriptor)) {
        myLibraryTypes.put(descriptor, libraryType);
      }
    }
  }

  private static Library createLibraryFromRoots(
    List<OrderRoot> roots,
    @Nullable LibraryType<?> libraryType,
    LibraryTable.ModifiableModel moduleLibrariesModel,
    @Nullable Function<? super LibraryType<?>, ? extends LibraryProperties<?>> defaultPropertiesFactory
  ) {
    var kind = libraryType == null ? null : libraryType.getKind();
    var library = moduleLibrariesModel.createLibrary(null, kind);
    var libModel = (LibraryEx.ModifiableModelEx)library.getModifiableModel();
    if (defaultPropertiesFactory != null) {
      libModel.setProperties(defaultPropertiesFactory.apply(libraryType));
    }
    for (var root : roots) {
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

    var libraries = moduleLibrariesModel.getLibraries();
    if (libraries.length == 0) {
      return roots;
    }

    return roots.stream()
      .filter(root -> Arrays.stream(libraries).noneMatch(library -> ArrayUtil.contains(root.getFile(), library.getFiles(root.getType()))))
      .collect(Collectors.toList());
  }

  @Override
  public @NotNull List<Library> chooseElements() {
    var descriptors = new ArrayList<Pair<LibraryRootsComponentDescriptor, FileChooserDescriptor>>();
    for (var componentDescriptor : myLibraryTypes.keySet()) {
      descriptors.add(Pair.create(componentDescriptor, componentDescriptor.createAttachFilesChooserDescriptor(null)));
    }

    FileChooserDescriptor chooserDescriptor;
    if (descriptors.size() == 1) {
      chooserDescriptor = descriptors.get(0).getSecond();
    }
    else {
      chooserDescriptor = new FileChooserDescriptor(true, true, true, false, true, false) {
        @Override
        public boolean isFileSelectable(@Nullable VirtualFile file) {
          for (var pair : descriptors) {
            if (pair.getSecond().isFileSelectable(file)) {
              return true;
            }
          }
          return false;
        }
      };
    }
    chooserDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, myModule);

    var project = myModule.getProject();
    var files = FileChooser.chooseFiles(chooserDescriptor, myParentComponent, project, ProjectUtil.guessProjectDir(project));
    if (files.length == 0) {
      return Collections.emptyList();
    }

    var suitableDescriptors = new ArrayList<LibraryRootsComponentDescriptor>();
    for (var pair : descriptors) {
      if (acceptAll(pair.getSecond(), files)) {
        suitableDescriptors.add(pair.getFirst());
      }
    }

    LibraryRootsComponentDescriptor rootsComponentDescriptor;
    LibraryType<?> libraryType = null;
    if (suitableDescriptors.size() == 1) {
      rootsComponentDescriptor = suitableDescriptors.get(0);
      libraryType = myLibraryTypes.get(rootsComponentDescriptor);
    }
    else {
      rootsComponentDescriptor = myDefaultDescriptor;
    }
    var chosenRoots = RootDetectionUtil.detectRoots(Arrays.asList(files), myParentComponent, project, rootsComponentDescriptor);

    return createLibrariesFromRoots(chosenRoots, libraryType, myModuleLibrariesModel, myDefaultPropertiesFactory);
  }

  @TestOnly
  public static @NotNull List<Library> createLibrariesFromRoots(List<OrderRoot> chosenRoots, LibraryTable.ModifiableModel moduleLibrariesModel) {
    return createLibrariesFromRoots(chosenRoots, null, moduleLibrariesModel, null);
  }

  private static List<Library> createLibrariesFromRoots(
    List<OrderRoot> chosenRoots,
    LibraryType<?> libraryType,
    LibraryTable.ModifiableModel moduleLibrariesModel,
    @Nullable Function<? super LibraryType<?>, ? extends LibraryProperties<?>> defaultPropertiesFactory
  ) {
    var roots = filterAlreadyAdded(chosenRoots, moduleLibrariesModel);
    if (roots.isEmpty()) {
      return Collections.emptyList();
    }

    Map<VirtualFile, List<OrderRoot>> byFile = roots.stream().collect(Collectors.groupingBy(OrderRoot::getFile, LinkedHashMap::new, Collectors.toList()));
    @SuppressWarnings("SSBasedInspection") Predicate<List<OrderRoot>> containsClasses = it -> it.stream().anyMatch(root -> root.getType().equals(OrderRootType.CLASSES));
    //noinspection SSBasedInspection
    if (byFile.values().stream().allMatch(containsClasses)) {
      var addedLibraries = new ArrayList<Library>();
      for (var rootsForFile : byFile.values()) {
        addedLibraries.add(createLibraryFromRoots(rootsForFile, libraryType, moduleLibrariesModel, defaultPropertiesFactory));
      }
      return addedLibraries;
    }
    else {
      return List.of(createLibraryFromRoots(roots, libraryType, moduleLibrariesModel, defaultPropertiesFactory));
    }
  }

  private static boolean acceptAll(FileChooserDescriptor descriptor, VirtualFile[] files) {
    for (var file : files) {
      if (!descriptor.isFileSelectable(file)) {
        return false;
      }
    }
    return true;
  }
}
