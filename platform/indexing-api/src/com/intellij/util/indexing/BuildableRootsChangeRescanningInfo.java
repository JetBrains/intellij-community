// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
@ApiStatus.Experimental
public abstract class BuildableRootsChangeRescanningInfo {

  @NotNull
  public static BuildableRootsChangeRescanningInfo newInstance() {
    return EntityIndexingService.getInstance().createBuildableInfoBuilder();
  }

  @NotNull
  public abstract BuildableRootsChangeRescanningInfo addModule(@NotNull Module module);

  @NotNull
  public abstract BuildableRootsChangeRescanningInfo addInheritedSdk();

  @NotNull
  public abstract BuildableRootsChangeRescanningInfo addSdk(@NotNull Sdk sdk);

  @NotNull
  public abstract BuildableRootsChangeRescanningInfo addLibrary(@NotNull Library library);

  @NotNull
  public abstract RootsChangeRescanningInfo buildInfo();
}
