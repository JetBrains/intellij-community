// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.DirtyFilesHolder;

import java.io.File;
import java.nio.file.Path;

/**
 * Determines the set of targets and files from them which should be recompiled during the build. Normally you should use {@link DirtyFilesHolder#processDirtyFiles(org.jetbrains.jps.builders.FileProcessor)}
 * instead of calling methods of this class directly. Usually methods from this class are used to optimize the build process, e.g. if in normal
 * cases you run a costly dependency analysis to determine which files should be marked as dirty you may skip this if
 * {@link #isBuildForcedForAllTargets(BuildTargetType)} returns {@code true} so you'll know that the all targets of the specified type will
 * be recompiled anyway.
 */
public abstract class CompileScope {
  /**
   * Determines whether {@code file} is included into the scope as part of {@code target}. A file may belong to several targets
   * (e.g., if there are two artifacts which are configured to copy the same source file to different output directories), so a file may be
   * affected as part of one target and not affected as part of another. Note that even a file is included into the scope it doesn't mean
   * that it should be recompiled: if incremental compilation is invoked and the file and its dependencies weren't changed since last
   * compilation it should not be recompiled.
   *
   * @return {@code true} if {@code file} is included into the scope as part of {@code target}
   */
  public abstract boolean isAffected(BuildTarget<?> target, @NotNull Path file);

  /**
   * @deprecated Use {@link #isAffected(BuildTarget, Path)}
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  public final boolean isAffected(BuildTarget<?> target, @NotNull File file) {
    return isAffected(target, file.toPath());
  }

  /**
   * @return {@code true} if at least one file from {@code target} is included into the scope
   */
  public abstract boolean isAffected(@NotNull BuildTarget<?> target);

  /**
   * Returns {@code true} if {@code target} is included into the scope as a whole, in particular this means that all files from the target are affected.
   * However this method may return {@code false} even if {@link #isAffected(BuildTarget, File)} is {@code true} for all files from the target
   * (e.g. if 'Compile Files' action is invoked for all files from the target).
   */
  public abstract boolean isWholeTargetAffected(@NotNull BuildTarget<?> target);

  /**
   * @return {@code true} if all files from all targets of type {@code type} are included into the scope
   */
  public abstract boolean isAllTargetsOfTypeAffected(@NotNull BuildTargetType<?> type);

  /**
   * @return {@code true} if all files from {@code target} should be recompiled even if they weren't changed since last compilation
   */
  public abstract boolean isBuildForced(@NotNull BuildTarget<?> target);

  /**
   * @return {@code true} if all files from all targets of type {@code targetType} should be recompiled even if they weren't changed since last compilation
   */
  public abstract boolean isBuildForcedForAllTargets(@NotNull BuildTargetType<?> targetType);

  /**
   * @return {@code true} if files from targets of type {@code targetType} should be recompiled only if they or some files they depend on
   * were changed since last compilation
   */
  public abstract boolean isBuildIncrementally(@NotNull BuildTargetType<?> targetType);

  public abstract void markIndirectlyAffected(BuildTarget<?> target, @NotNull Path file);
}
