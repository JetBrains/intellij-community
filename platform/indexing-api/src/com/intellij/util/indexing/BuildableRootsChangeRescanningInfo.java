// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.NotNull;

@NonExtendable
@Experimental
public abstract class BuildableRootsChangeRescanningInfo implements RootsChangeRescanningInfo {

  @Internal
  public BuildableRootsChangeRescanningInfo() {
  }

  public static @NotNull BuildableRootsChangeRescanningInfo newInstance() {
    return EntityIndexingService.getInstance().createBuildableInfoBuilder();
  }

  public abstract @NotNull BuildableRootsChangeRescanningInfo addInheritedSdk();

  public abstract @NotNull BuildableRootsChangeRescanningInfo addSdk(@NotNull Sdk sdk);

  public abstract @NotNull BuildableRootsChangeRescanningInfo addLibrary(@NotNull Library library);

  public abstract @NotNull RootsChangeRescanningInfo buildInfo();
}
