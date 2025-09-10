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
package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsProject;

import java.io.File;
import java.nio.file.Path;

/**
 * Use {@link JpsModelSerializationDataService#getBaseDirectory(JpsProject)} to get the directory where the project configuration is stored.
 */
@ApiStatus.Internal
public interface JpsProjectSerializationDataExtension extends JpsElement {
  /**
   * @deprecated Use {@link #getBaseDirectoryPath()} instead.
   */
  @Deprecated
  @NotNull File getBaseDirectory();
  
  @NotNull Path getBaseDirectoryPath();
}
