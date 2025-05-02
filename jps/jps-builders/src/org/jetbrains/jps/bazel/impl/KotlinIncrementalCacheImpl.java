// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCache;
import org.jetbrains.kotlin.load.kotlin.incremental.components.JvmPackagePartProto;

import java.util.Collection;
import java.util.List;

public class KotlinIncrementalCacheImpl implements IncrementalCache {
  @Override
  public @NotNull Collection<String> getObsoletePackageParts() {
    return List.of();
  }

  @Override
  public @NotNull Collection<String> getObsoleteMultifileClasses() {
    return List.of();
  }

  @Override
  public @Nullable Collection<String> getStableMultifileFacadeParts(@NotNull String s) {
    return List.of();
  }

  @Override
  public @Nullable JvmPackagePartProto getPackagePartData(@NotNull String s) {
    return null;
  }

  @Override
  public @Nullable byte[] getModuleMappingData() {
    return new byte[0];
  }

  @Override
  public @NotNull String getClassFilePath(@NotNull String s) {
    return "";
  }

  @Override
  public void close() {

  }
}
