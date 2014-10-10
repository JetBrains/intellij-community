/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;

import java.io.File;

/**
 * @author nik
 */
public abstract class ArtifactRootCopyingHandlerProvider {
  @Nullable
  public FileCopyingHandler createCustomHandler(@NotNull JpsArtifact artifact,
                                                @NotNull File root,
                                                @NotNull JpsPackagingElement contextElement,
                                                @NotNull JpsModel model,
                                                @NotNull BuildDataPaths buildDataPaths) {
    return createCustomHandler(artifact, root, model, buildDataPaths);
  }

  /**
   * @deprecated override {@link #createCustomHandler(JpsArtifact, File, JpsPackagingElement, JpsModel, BuildDataPaths)} instead
   */
  @Nullable
  public FileCopyingHandler createCustomHandler(@NotNull JpsArtifact artifact, @NotNull File root, @NotNull JpsModel model,
                                                @NotNull BuildDataPaths buildDataPaths) {
    throw new UnsupportedOperationException();
  }
}
