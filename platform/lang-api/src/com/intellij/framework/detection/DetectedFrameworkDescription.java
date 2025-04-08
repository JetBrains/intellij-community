// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection;

import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Describes the detected framework
 *
 * @see FrameworkDetector#detect(Collection, FrameworkDetectionContext)
 */
public abstract class DetectedFrameworkDescription {
  /**
   * @return files accepted by {@link FrameworkDetector} which belong to detected framework
   */
  public abstract @NotNull @Unmodifiable Collection<? extends VirtualFile> getRelatedFiles();

  /**
   * @return text to show in 'Setup Frameworks' dialog when this framework is selected
   */
  public abstract @NotNull @NlsContexts.Label String getSetupText();

  /**
   * @return detector which detects this framework
   */
  public abstract @NotNull FrameworkDetector getDetector();

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

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();
}
