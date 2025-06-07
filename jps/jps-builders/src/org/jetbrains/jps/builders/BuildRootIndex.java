// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.CompileContext;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public interface BuildRootIndex {

  @NotNull
  <R extends BuildRootDescriptor> List<R> getTargetRoots(@NotNull BuildTarget<R> target, @Nullable CompileContext context);

  @NotNull
  <R extends BuildRootDescriptor> List<R> getTempTargetRoots(@NotNull BuildTarget<R> target, @NotNull CompileContext context);

  @NotNull
  <R extends BuildRootDescriptor> List<R> getRootDescriptors(@NotNull File root,
                                                             @Nullable Collection<? extends BuildTargetType<? extends BuildTarget<R>>> types,
                                                             @Nullable CompileContext context);

  <R extends BuildRootDescriptor> void associateTempRoot(@NotNull CompileContext context, @NotNull BuildTarget<R> target, @NotNull R root);

  @NotNull
  Collection<? extends BuildRootDescriptor> clearTempRoots(@NotNull CompileContext context);

  @Nullable
  <R extends BuildRootDescriptor> R findParentDescriptor(@NotNull File file, @NotNull Collection<? extends BuildTargetType<? extends BuildTarget<R>>> types,
                                                         @Nullable CompileContext context);

  @NotNull
  <R extends BuildRootDescriptor> Collection<R> findAllParentDescriptors(@NotNull File file,
                                                                         @Nullable Collection<? extends BuildTargetType<? extends BuildTarget<R>>> types,
                                                                         @Nullable CompileContext context);

  @NotNull
  <R extends BuildRootDescriptor> Collection<R> findAllParentDescriptors(@NotNull File file, @Nullable CompileContext context);

  @Nullable
  JavaSourceRootDescriptor findJavaRootDescriptor(@Nullable CompileContext context, File file);

  @NotNull
  FileFilter getRootFilter(@NotNull BuildRootDescriptor descriptor);

  boolean isDirectoryAccepted(@NotNull Path dir, @NotNull BuildRootDescriptor descriptor);

  boolean isFileAccepted(@NotNull Path file, @NotNull BuildRootDescriptor descriptor);
}
