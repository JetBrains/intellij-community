// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.StorageManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCache;
import org.jetbrains.kotlin.load.kotlin.incremental.components.JvmPackagePartProto;

import java.util.Collection;
import java.util.List;

public class KotlinIncrementalCacheImpl implements IncrementalCache {
  StorageManager myStorageManager;
  public KotlinIncrementalCacheImpl(StorageManager storageManager) {
    myStorageManager = storageManager;
  }

  /**
   * TODO:
   *  Returns names of obsolete package parts that should be removed from .kotlin_module.
   *  The logic of original function was as follows:
   *  1. Collect all deleted and modified source files (dirty+removed)
   *  2. Get output files generated from these sources
   *  3. Extract package/className from outputs
   *  4. Filter names to only include file facades (like AppKt) or multifile parts (available in both bytecode and metadata)
   *  e.g. kotlin.Metadata.Kind == FILE_FACADE or MULTIFILE_CLASS_PART
   *  5. Return JVM names that will be cleaned from .kotlin_module
   */
  @Override
  public @NotNull Collection<String> getObsoletePackageParts() {
    return List.of();
  }

  /**
   * TODO:
   *  This function should return .kotlin_module file content as byte array from the previous compilation
   *  or null if it is rebuild or first compilation
   *  Expected to return the state of the last successful compilation
   */
  @Override
  public @Nullable byte[] getModuleMappingData() {

    return new byte[0];
  }

  // No need to implement functions below because they are not used and will be removed in the future
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
  public @NotNull String getClassFilePath(@NotNull String s) {
    return "";
  }

  @Override
  public void close() {

  }
}
