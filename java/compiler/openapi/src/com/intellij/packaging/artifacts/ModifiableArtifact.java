/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.packaging.artifacts;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.packaging.elements.CompositePackagingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ModifiableArtifact extends Artifact {

  void setBuildOnMake(boolean enabled);

  void setOutputPath(@NlsSafe String outputPath);

  void setName(@NotNull @NlsSafe String name);

  void setRootElement(@NotNull CompositePackagingElement<?> root);

  /**
   * Sets custom properties corresponding to {@code provider} in the artifact configuration. If {@code properties} is {@code null} custom
   * properties corresponding to {@code provider} are removed.
   */
  void setProperties(@NotNull ArtifactPropertiesProvider provider, @Nullable ArtifactProperties<?> properties);

  void setArtifactType(@NotNull ArtifactType selected);
}
