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
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.ui.LibraryElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class LibraryPackagingElement extends ComplexPackagingElement<LibraryPackagingElement> {
  @NonNls public static final String LIBRARY_NAME_ATTRIBUTE = "name";
  @NonNls public static final String MODULE_NAME_ATTRIBUTE = "module-name";
  @NonNls public static final String LIBRARY_LEVEL_ATTRIBUTE = "level";
  private String myLevel;
  private String myLibraryName;
  private String myModuleName;

  public LibraryPackagingElement() {
    super(LibraryElementType.LIBRARY_ELEMENT_TYPE);
  }

  public LibraryPackagingElement(String level, String libraryName, String moduleName) {
    super(LibraryElementType.LIBRARY_ELEMENT_TYPE);
    myLevel = level;
    myLibraryName = libraryName;
    myModuleName = moduleName;
  }

  public List<? extends PackagingElement<?>> getSubstitution(@NotNull PackagingElementResolvingContext context, @NotNull ArtifactType artifactType) {
    final Library library = findLibrary(context);
    if (library != null) {
      final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
      final List<PackagingElement<?>> elements = new ArrayList<>();
      for (VirtualFile file : files) {
        String localPath = PathUtil.getLocalPath(file);
        if (localPath != null) {
          final String path = FileUtil.toSystemIndependentName(localPath);
          elements.add(file.isDirectory() && file.isInLocalFileSystem() ? new DirectoryCopyPackagingElement(path) : new FileCopyPackagingElement(path));
        }
      }
      return elements;
    }
    return null;
  }

  @NotNull
  @Override
  public PackagingElementOutputKind getFilesKind(PackagingElementResolvingContext context) {
    final Library library = findLibrary(context);
    return library != null ? getKindForLibrary(library) : PackagingElementOutputKind.OTHER;
  }

  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new LibraryElementPresentation(myLibraryName, myLevel, myModuleName, findLibrary(context), context);
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    if (!(element instanceof LibraryPackagingElement)) {
      return false;
    }

    LibraryPackagingElement packagingElement = (LibraryPackagingElement)element;
    return myLevel != null && myLibraryName != null && myLevel.equals(packagingElement.getLevel())
           && myLibraryName.equals(packagingElement.getLibraryName())
           && Comparing.equal(myModuleName, packagingElement.getModuleName());
  }

  public LibraryPackagingElement getState() {
    return this;
  }

  public void loadState(LibraryPackagingElement state) {
    myLevel = state.getLevel();
    myLibraryName = state.getLibraryName();
    myModuleName = state.getModuleName();
  }

  @Attribute(LIBRARY_LEVEL_ATTRIBUTE)
  public String getLevel() {
    return myLevel;
  }

  public void setLevel(String level) {
    myLevel = level;
  }

  @Attribute(LIBRARY_NAME_ATTRIBUTE)
  public String getLibraryName() {
    return myLibraryName;
  }

  public void setLibraryName(String libraryName) {
    myLibraryName = libraryName;
  }

  @Attribute(MODULE_NAME_ATTRIBUTE)
  public String getModuleName() {
    return myModuleName;
  }

  public void setModuleName(String moduleName) {
    myModuleName = moduleName;
  }

  @Override
  public String toString() {
    return "lib:" + myLibraryName + "(" + (myModuleName != null ? "module " + myModuleName: myLevel ) + ")";
  }

  @Nullable
  public Library findLibrary(@NotNull PackagingElementResolvingContext context) {
    if (myModuleName == null) {
      return context.findLibrary(myLevel, myLibraryName);
    }
    final ModulesProvider modulesProvider = context.getModulesProvider();
    final Module module = modulesProvider.getModule(myModuleName);
    if (module != null) {
      for (OrderEntry entry : modulesProvider.getRootModel(module).getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry) {
          final LibraryOrderEntry libraryEntry = (LibraryOrderEntry)entry;
          if (libraryEntry.isModuleLevel()) {
            final String libraryName = libraryEntry.getLibraryName();
            if (libraryName != null && libraryName.equals(myLibraryName)) {
              return libraryEntry.getLibrary();
            }
          }
        }
      }
    }
    return null;
  }

  public static PackagingElementOutputKind getKindForLibrary(final Library library) {
    boolean containsDirectories = false;
    boolean containsJars = false;
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      if (file.isInLocalFileSystem()) {
        containsDirectories = true;
      }
      else {
        containsJars = true;
      }
    }
    return new PackagingElementOutputKind(containsDirectories, containsJars);
  }
}
