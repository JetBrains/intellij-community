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
package com.intellij.framework.detection;

import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Describes the detected framework
 *
 * @see FrameworkDetector#detect(java.util.Collection, FrameworkDetectionContext)
 * @author nik
 */
public abstract class DetectedFrameworkDescription {
  /**
   * @return files accepted by {@link FrameworkDetector} which belong to detected framework
   */
  @NotNull
  public abstract Collection<? extends VirtualFile> getRelatedFiles();

  /**
   * @return text to show in 'Setup Frameworks' dialog when this framework is selected
   */
  @NotNull
  public abstract String getSetupText();

  /**
   * @return detector which detects this framework
   */
  @NotNull
  public abstract FrameworkDetector getDetector();

  /**
   * Check whether the framework can be added to the project or not
   * @param allDetectedFrameworks all frameworks currently detected in the project
   * @return {@code true} if
   */
  public boolean canSetupFramework(@NotNull Collection<? extends DetectedFrameworkDescription> allDetectedFrameworks) {
    return true;
  }

  /**
   * Setup support for the framework in the project (e.g. add required facets or libraries)
   * @param modifiableModelsProvider used to modify the project structure
   * @param modulesProvider can be used to find modules
   */
  public abstract void setupFramework(@NotNull ModifiableModelsProvider modifiableModelsProvider, @NotNull ModulesProvider modulesProvider);

  public abstract boolean equals(Object obj);

  public abstract int hashCode();
}
