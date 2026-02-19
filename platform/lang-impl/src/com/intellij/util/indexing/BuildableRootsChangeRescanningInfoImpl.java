// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.platform.workspace.jps.entities.LibraryId;
import com.intellij.platform.workspace.jps.entities.LibraryTableId;
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator;
import com.intellij.util.SmartList;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge;
import kotlin.Pair;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Internal
public final class BuildableRootsChangeRescanningInfoImpl extends BuildableRootsChangeRescanningInfo {
  private boolean hasInheritedSdk;
  private final List<Pair<String, String>> sdks = new SmartList<>();
  private final List<LibraryId> libraries = new SmartList<>();

  @Internal
  public BuildableRootsChangeRescanningInfoImpl() {
  }

  @Override
  public @NotNull BuildableRootsChangeRescanningInfo addInheritedSdk() {
    hasInheritedSdk = true;
    return this;
  }

  @Override
  public @NotNull BuildableRootsChangeRescanningInfo addSdk(@NotNull Sdk sdk) {
    sdks.add(new Pair<>(sdk.getName(), sdk.getSdkType().getName()));
    return this;
  }

  @Override
  public @NotNull BuildableRootsChangeRescanningInfo addLibrary(@NotNull Library library) {
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
  public @NotNull RootsChangeRescanningInfo buildInfo() {
    return new BuiltRescanningInfo(hasInheritedSdk, List.copyOf(sdks), List.copyOf(libraries));
  }

  static class BuiltRescanningInfo implements RootsChangeRescanningInfo {
    public final boolean hasInheritedSdk;
    public final @NotNull List<Pair<String, String>> sdks;
    public final @NotNull List<LibraryId> libraries;

    BuiltRescanningInfo(boolean sdk,
                        @NotNull List<Pair<String, String>> sdks,
                        @NotNull List<LibraryId> libraries) {
      hasInheritedSdk = sdk;
      this.sdks = sdks;
      this.libraries = libraries;
    }
  }
}