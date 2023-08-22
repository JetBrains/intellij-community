// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.platform.workspace.jps.entities.LibraryId;
import com.intellij.platform.workspace.jps.entities.LibraryTableId;
import com.intellij.platform.workspace.jps.entities.ModuleId;
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class BuildableRootsChangeRescanningInfoImpl extends BuildableRootsChangeRescanningInfoEx {
  private final Set<ModuleId> modules = new SmartHashSet<>();
  private boolean hasInheritedSdk;
  private final List<Pair<String, String>> sdks = new SmartList<>();
  private final List<LibraryId> libraries = new SmartList<>();
  private final List<WorkspaceEntity> entities = new SmartList<>();

  @Override
  @NotNull
  public BuildableRootsChangeRescanningInfo addModule(@NotNull com.intellij.openapi.module.Module module) {
    modules.add(new ModuleId(module.getName()));
    return this;
  }

  @Override
  @NotNull
  public BuildableRootsChangeRescanningInfo addInheritedSdk() {
    hasInheritedSdk = true;
    return this;
  }

  @Override
  @NotNull
  public BuildableRootsChangeRescanningInfo addSdk(@NotNull Sdk sdk) {
    sdks.add(new Pair<>(sdk.getName(), sdk.getSdkType().getName()));
    return this;
  }

  @Override
  @NotNull
  public BuildableRootsChangeRescanningInfo addLibrary(@NotNull Library library) {
    LibraryId libraryId;
    if (library instanceof LibraryBridge) {
      libraryId = ((LibraryBridge)library).getLibraryId();
    }
    else {
      String level = library.getTable().getTableLevel();
      LibraryTableId libraryTableId = LibraryNameGenerator.INSTANCE.getLibraryTableId(level);
      String libraryName = library.getName();
      libraryId = new LibraryId(libraryName, libraryTableId);
    }
    libraries.add(libraryId);
    return this;
  }

  @Override
  public @NotNull BuildableRootsChangeRescanningInfo addWorkspaceEntity(@NotNull WorkspaceEntity entity) {
    entities.add(entity);
    return this;
  }

  @NotNull
  Collection<ModuleId> getModules() {
    return modules;
  }

  public boolean hasInheritedSdk() {
    return hasInheritedSdk;
  }

  @NotNull
  Collection<Pair<String, String>> getSdks() {
    return sdks;
  }

  @NotNull
  Collection<LibraryId> getLibraries() {
    return libraries;
  }

  @NotNull
  Collection<WorkspaceEntity> getWorkspaceEntities() {
    return entities;
  }
}