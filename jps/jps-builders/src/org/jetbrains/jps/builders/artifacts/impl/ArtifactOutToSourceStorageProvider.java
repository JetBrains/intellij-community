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
package org.jetbrains.jps.builders.artifacts.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class ArtifactOutToSourceStorageProvider extends StorageProvider<ArtifactOutputToSourceMapping> {
  public static final ArtifactOutToSourceStorageProvider INSTANCE = new ArtifactOutToSourceStorageProvider();

  private ArtifactOutToSourceStorageProvider() {
  }

  @NotNull
  @Override
  public ArtifactOutputToSourceMapping createStorage(File targetDataDir) throws IOException {
    return new ArtifactOutputToSourceMapping(new File(targetDataDir, "out-src" + File.separator + "data"));
  }
}
