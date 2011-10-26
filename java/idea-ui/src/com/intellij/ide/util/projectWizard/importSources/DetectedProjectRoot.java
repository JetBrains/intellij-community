/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard.importSources;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author nik
 */
public abstract class DetectedProjectRoot {
  private final File myDirectory;

  protected DetectedProjectRoot(@NotNull File directory) {
    myDirectory = directory;
  }

  public File getDirectory() {
    return myDirectory;
  }

  @NotNull
  public abstract String getRootTypeName();

  @Nullable
  public DetectedProjectRoot combineWith(@NotNull DetectedProjectRoot root) {
    return null;
  }

  public boolean canContainRoot(@NotNull DetectedProjectRoot root) {
    return true;
  }
}
