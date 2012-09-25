package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.fs.RootDescriptor;

import java.io.File;
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
  <R extends BuildRootDescriptor> List<R> getRootDescriptors(@NotNull File root, @NotNull Collection<? extends BuildTargetType<? extends BuildTarget<R>>> types,
                                                             @Nullable CompileContext context);

  <R extends BuildRootDescriptor> void associateTempRoot(@NotNull CompileContext context, @NotNull BuildTarget<R> target, @NotNull R root);

  @NotNull
  Collection<? extends BuildRootDescriptor> clearTempRoots(@NotNull CompileContext context);

  @Nullable
  <R extends BuildRootDescriptor> R findParentDescriptor(@NotNull File file, @NotNull Collection<? extends BuildTargetType<? extends BuildTarget<R>>> types,
                                                         @Nullable CompileContext context);

  @NotNull
  <R extends BuildRootDescriptor> Collection<R> findAllParentDescriptors(@NotNull File file,
                                                                         @NotNull Collection<? extends BuildTargetType<? extends BuildTarget<R>>> types,
                                                                         @Nullable CompileContext context);

  @Nullable
  RootDescriptor getModuleAndRoot(@Nullable CompileContext context, File file);
}
