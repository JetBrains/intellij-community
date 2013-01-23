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
package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.CompileContext;

import java.io.File;
import java.io.FileFilter;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public interface BuildRootIndex {

  @NotNull
  <R extends BuildRootDescriptor> List<R> getTargetRoots(@NotNull BuildTarget<R> target, @Nullable CompileContext context);

  @NotNull
  <R extends BuildRootDescriptor> List<R> getTempTargetRoots(@NotNull BuildTarget<R> target, @NotNull CompileContext context);

  @NotNull
  <R extends BuildRootDescriptor> List<R> getRootDescriptors(@NotNull File root, @Nullable Collection<? extends BuildTargetType<? extends BuildTarget<R>>> types,
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

  boolean isDirectoryAccepted(@NotNull File dir, @NotNull BuildRootDescriptor descriptor);

  boolean isFileAccepted(@NotNull File file, @NotNull BuildRootDescriptor descriptor);
}
