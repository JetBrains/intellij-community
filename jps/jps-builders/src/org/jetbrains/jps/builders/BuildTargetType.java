// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsModel;

import java.util.List;

/**
 * The type of a build target. For example, there is a build target type for Java production and another for Java tests.
 *
 * @see org.jetbrains.jps.incremental.BuilderService#getTargetTypes()
 */
public abstract class BuildTargetType<T extends BuildTarget<?>> {
  private final String myTypeId;
  private final boolean myFileBased;

  /**
   * Creates type of targets which are not {@link #isFileBased() file based}, consider using {@link #BuildTargetType(String, boolean)} instead
   */
  protected BuildTargetType(@NotNull String typeId) {
    this(typeId, false);
  }

  /**
   * @param typeId ID of this target type, it must be unique among all instances of {@link BuildTargetType}
   * @param fileBased {@code true} if targets of this type operate only on files under their source roots
   * @see #isFileBased()
   */
  protected BuildTargetType(@NotNull String typeId, boolean fileBased) {
    myTypeId = typeId;
    myFileBased = fileBased;
  }

  public final @NotNull String getTypeId() {
    return myTypeId;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof BuildTargetType && ((BuildTargetType<?>)obj).myTypeId.equals(myTypeId);
  }

  @Override
  public int hashCode() {
    return myTypeId.hashCode();
  }

  /**
   * Finds all targets of the given type that exist in the given project.
   * @param model the model instance representing a project.
   * @return the list of targets.
   */
  public abstract @NotNull List<T> computeAllTargets(@NotNull JpsModel model);

  /**
   * @return implementation of {@link BuildTargetLoader} class which will be used to deserialize targets of this type from compiler caches
   */
  public abstract @NotNull BuildTargetLoader<T> createLoader(@NotNull JpsModel model);

  /**
   * Return {@code true} if targets of this type operate only on files under their {@link BuildTarget#computeRootDescriptors source roots}.
   * In that case the build system may perform some optimizations if where are no roots for a particular {@link BuildTarget} instance of this type.
   * <p>Most of the target types are file based. A target may be not file based e.g. if it produces output using information from the
   * project configuration only.</p>
   */
  public final boolean isFileBased() {
    return myFileBased;
  }
}
