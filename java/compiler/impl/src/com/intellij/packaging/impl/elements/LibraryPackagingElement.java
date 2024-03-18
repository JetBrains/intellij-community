// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements;

import com.intellij.java.workspace.entities.LibraryFilesPackagingElementEntity;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.ui.LibraryElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.platform.workspace.jps.entities.LibraryId;
import com.intellij.platform.workspace.jps.entities.LibraryTableId;
import com.intellij.platform.workspace.jps.entities.ModuleId;
import com.intellij.platform.workspace.storage.EntitySource;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

  @Override
  public List<? extends PackagingElement<?>> getSubstitution(@NotNull PackagingElementResolvingContext context,
                                                             @NotNull ArtifactType artifactType) {
    final Library library = findLibrary(context);
    if (library != null) {
      final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
      final List<PackagingElement<?>> elements = new ArrayList<>();
      for (VirtualFile file : files) {
        String localPath = PathUtil.getLocalPath(file);
        if (localPath != null) {
          final String path = FileUtil.toSystemIndependentName(localPath);
          elements.add(file.isDirectory() && file.isInLocalFileSystem()
                       ? new DirectoryCopyPackagingElement(path)
                       : new FileCopyPackagingElement(path));
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

  @Override
  @NotNull
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    if (myStorage == null) {
      return new LibraryElementPresentation(myLibraryName, myLevel, myModuleName, findLibrary(context), context);
    }
    LibraryFilesPackagingElementEntity entity = (LibraryFilesPackagingElementEntity)getThisEntity();
    return new LibraryElementPresentation(getMyLibraryName(entity), getMyLevel(entity), getMyModuleName(entity), findLibrary(context),
                                          context);
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    if (!(element instanceof LibraryPackagingElement packagingElement)) {
      return false;
    }
    String level;
    String libraryName;
    String moduleName;


    if (myStorage == null) {
      level = myLevel;
      libraryName = myLibraryName;
      moduleName = myModuleName;
    }
    else {
      LibraryFilesPackagingElementEntity entity = (LibraryFilesPackagingElementEntity)getThisEntity();
      level = getMyLevel(entity);
      libraryName = getMyLibraryName(entity);
      moduleName = getMyModuleName(entity);
    }

    return level != null && libraryName != null && level.equals(packagingElement.getLevel())
           && libraryName.equals(packagingElement.getLibraryName())
           && Objects.equals(moduleName, packagingElement.getModuleName());
  }

  @Override
  public LibraryPackagingElement getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull LibraryPackagingElement state) {
    myLevel = state.getLevel();
    myLibraryName = state.getLibraryName();
    myModuleName = state.getModuleName();
  }

  @Attribute(LIBRARY_LEVEL_ATTRIBUTE)
  public String getLevel() {
    return myStorage == null ? myLevel : getMyLevel((LibraryFilesPackagingElementEntity)getThisEntity());
  }

  public void setLevel(String level) {
    String levelBefore = getLevel();
    this.update(
      () -> myLevel = level,
      (builder, entity) -> {
        if (levelBefore.equals(level)) return;
        builder.modifyEntity(LibraryFilesPackagingElementEntity.Builder.class, entity, ent -> {
          LibraryId libraryId = ent.getLibrary();
          if (libraryId != null) {
            LibraryTableId newTableId;
            if ("project".equals(level)) {
              newTableId = LibraryTableId.ProjectLibraryTableId.INSTANCE;
            }
            else if ("module".equals(level)) {
              throw new RuntimeException("Cannot set module level without module name");
            }
            else {
              newTableId = new LibraryTableId.GlobalLibraryTableId(level);
            }
            ent.setLibrary(libraryId.copy(libraryId.getName(), newTableId));
          }
          return Unit.INSTANCE;
        });
      }
    );
  }

  @Attribute(LIBRARY_NAME_ATTRIBUTE)
  public String getLibraryName() {
    return myStorage == null ? myLibraryName : getMyLibraryName((LibraryFilesPackagingElementEntity)getThisEntity());
  }

  public void setLibraryName(String libraryName) {
    this.update(
      () -> myLibraryName = libraryName,
      (builder, entity) -> {
        builder.modifyEntity(LibraryFilesPackagingElementEntity.Builder.class, entity, ent -> {
          LibraryId libraryId = ent.getLibrary();
          if (libraryId != null) {
            ent.setLibrary(libraryId.copy(libraryName, libraryId.getTableId()));
          }
          return Unit.INSTANCE;
        });
      }
    );
  }

  @Attribute(MODULE_NAME_ATTRIBUTE)
  public String getModuleName() {
    return myStorage == null ? myModuleName : getMyModuleName((LibraryFilesPackagingElementEntity)getThisEntity());
  }

  public void setModuleName(String moduleName) {
    String moduleNameBefore = getModuleName();
    this.update(
      () -> myModuleName = moduleName,
      (builder, entity) -> {

        if (Objects.equals(moduleNameBefore, moduleName)) return;
        builder.modifyEntity(LibraryFilesPackagingElementEntity.Builder.class, entity, ent -> {
          LibraryId libraryId = ent.getLibrary();
          if (libraryId != null) {
            LibraryTableId newTableId = new LibraryTableId.ModuleLibraryTableId(new ModuleId(moduleName));
            ent.setLibrary(libraryId.copy(libraryId.getName(), newTableId));
          }
          return Unit.INSTANCE;
        });
      }
    );
  }

  @Override
  public String toString() {
    String level = myLevel;
    String libraryName = myLibraryName;
    String moduleName = myModuleName;


    if (myStorage != null) {
      LibraryFilesPackagingElementEntity entity = (LibraryFilesPackagingElementEntity)getThisEntity();
      level = getMyLevel(entity);
      libraryName = getMyLibraryName(entity);
      moduleName = getMyModuleName(entity);
    }

    return "lib:" + libraryName + "(" + (moduleName != null ? "module " + moduleName : level) + ")";
  }

  @Override
  public WorkspaceEntity getOrAddEntity(@NotNull MutableEntityStorage diff,
                                        @NotNull EntitySource source,
                                        @NotNull Project project) {
    WorkspaceEntity existingEntity = getExistingEntity(diff);
    if (existingEntity != null) return existingEntity;

    LibraryFilesPackagingElementEntity entity;
    if (myLibraryName == null) {
      entity = diff.addEntity(LibraryFilesPackagingElementEntity.create(source));
    }
    else {
      LibraryId id;
      if (myLevel.equals("module")) {
        id = new LibraryId(myLibraryName, new LibraryTableId.ModuleLibraryTableId(new ModuleId(myModuleName)));
      }
      else if (myLevel.equals("project")) {
        id = new LibraryId(myLibraryName, LibraryTableId.ProjectLibraryTableId.INSTANCE);
      }
      else {
        id = new LibraryId(myLibraryName, new LibraryTableId.GlobalLibraryTableId(myLevel));
      }
      entity = diff.addEntity(LibraryFilesPackagingElementEntity.create(source, o -> {
        o.setLibrary(id);
        return Unit.INSTANCE;
      }));
    }
    diff.getMutableExternalMapping(PackagingExternalMapping.key).addMapping(entity, this);
    return entity;
  }

  @Nullable
  public Library findLibrary(@NotNull PackagingElementResolvingContext context) {
    String level = myLevel;
    String myLibraryName1 = myLibraryName;
    String moduleName = myModuleName;


    if (myStorage != null) {
      LibraryFilesPackagingElementEntity entity = (LibraryFilesPackagingElementEntity)getThisEntity();
      level = getMyLevel(entity);
      myLibraryName1 = getMyLibraryName(entity);
      moduleName = getMyModuleName(entity);
    }

    if (moduleName == null && level != null && myLibraryName1 != null) {
      return context.findLibrary(level, myLibraryName1);
    }
    final ModulesProvider modulesProvider = context.getModulesProvider();
    final Module module;
    if (moduleName != null) {
      module = modulesProvider.getModule(moduleName);
      if (module != null) {
        for (OrderEntry entry : modulesProvider.getRootModel(module).getOrderEntries()) {
          if (entry instanceof LibraryOrderEntry libraryEntry && libraryEntry.isModuleLevel()) {
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

  private @Nullable String getMyLevel(LibraryFilesPackagingElementEntity entity) {

    LibraryId library = entity.getLibrary();
    String level = null;
    if (library != null) {
      level = library.getTableId().getLevel();
      if (!Objects.equals(level, myLevel)) {
        myLevel = level;
      }
    }
    else {
      if (myLevel != null) {
        myLevel = null;
      }
    }
    return level;
  }

  private @Nullable String getMyLibraryName(LibraryFilesPackagingElementEntity entity) {
    LibraryId library = entity.getLibrary();
    String libraryName = null;
    if (library != null) {
      libraryName = library.getName();
      if (!Objects.equals(libraryName, myLibraryName)) {
        myLibraryName = libraryName;
      }
    }
    else {
      if (myLibraryName != null) {
        myLibraryName = null;
      }
    }
    return libraryName;
  }

  private @Nullable String getMyModuleName(LibraryFilesPackagingElementEntity entity) {
    LibraryId library = entity.getLibrary();
    String moduleName = null;
    if (library != null) {
      LibraryTableId tableId = library.getTableId();
      if (tableId instanceof LibraryTableId.ModuleLibraryTableId) {
        moduleName = ((LibraryTableId.ModuleLibraryTableId)tableId).getModuleId().getName();
        if (!Objects.equals(moduleName, myModuleName)) {
          myModuleName = moduleName;
        }
      }
      else {
        if (myModuleName != null) {
          myModuleName = null;
        }
      }
    }
    else {
      if (myModuleName != null) {
        myModuleName = null;
      }
    }
    return moduleName;
  }
}
