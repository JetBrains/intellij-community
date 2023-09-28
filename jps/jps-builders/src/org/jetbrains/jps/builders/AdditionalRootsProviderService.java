// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataPaths;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Allows adding additional roots to other build targets.
 *
 * Implementations of this class are registered as Java services, by
 * creating a file META-INF/services/org.jetbrains.jps.builders.AdditionalRootsProviderService containing the qualified name of your implementation
 * class.
 */
public abstract class AdditionalRootsProviderService<R extends BuildRootDescriptor> {
  private final Collection<? extends BuildTargetType<? extends BuildTarget<R>>> myTargetTypes;

  /**
   * @param targetTypes types of target to which additional roots should be added
   */
  protected AdditionalRootsProviderService(@NotNull Collection<? extends BuildTargetType<? extends BuildTarget<R>>> targetTypes) {
    myTargetTypes = targetTypes;
  }

  public @NotNull Collection<? extends BuildTargetType<? extends BuildTarget<R>>> getTargetTypes() {
    return myTargetTypes;
  }

  /**
   * Override this method to return additional roots which should be added to {@link BuildTarget#computeRootDescriptors the roots} returned
   * by the {@code target} itself.
   */
  public @NotNull List<R> getAdditionalRoots(@NotNull BuildTarget<R> target, @NotNull BuildDataPaths dataPaths) {
    return Collections.emptyList();
  }
}
