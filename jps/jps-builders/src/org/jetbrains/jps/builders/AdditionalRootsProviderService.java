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
 * @author nik
 */
public abstract class AdditionalRootsProviderService<R extends BuildRootDescriptor> {
  private final Collection<? extends BuildTargetType<? extends BuildTarget<R>>> myTargetTypes;

  /**
   * @param targetTypes types of target to which additional roots should be added
   */
  protected AdditionalRootsProviderService(Collection<? extends BuildTargetType<? extends BuildTarget<R>>> targetTypes) {
    myTargetTypes = targetTypes;
  }

  public Collection<? extends BuildTargetType<? extends BuildTarget<R>>> getTargetTypes() {
    return myTargetTypes;
  }

  /**
   * Override this method to return additional roots which should be added to {@link BuildTarget#computeRootDescriptors the roots} returned
   * by the {@code target} itself.
   */
  @NotNull
  public List<R> getAdditionalRoots(@NotNull BuildTarget<R> target, BuildDataPaths dataPaths) {
    return Collections.emptyList();
  }
}
