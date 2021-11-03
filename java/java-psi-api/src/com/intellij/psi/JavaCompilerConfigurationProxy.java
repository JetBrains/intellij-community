// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Provides additional compiler options for a given module.
 *
 * Not an actual extension. Used to avoid dependency on the compiler module.
 */
public abstract class JavaCompilerConfigurationProxy {
  private static final ExtensionPointName<JavaCompilerConfigurationProxy> EP_NAME = new ExtensionPointName<>("com.intellij.javaCompilerConfigurationProxy");

  /**
   * Returns additional compiler options applicable to the given module, if any.
   * @see JavaCompilerConfigurationProxy#setAdditionalOptions(Project, Module, List<String>)
   */
  abstract protected List<String> getAdditionalOptionsImpl(@NotNull Project project, @NotNull Module module);

  /**
   * Sets additional compiler options applicable to the given module.
   * @see JavaCompilerConfigurationProxy#getAdditionalOptions(Project, Module)
   */
  abstract protected void setAdditionalOptionsImpl(@NotNull Project project, @NotNull Module module, @NotNull List<String> options);

  public static List<String> getAdditionalOptions(@NotNull Project project, @NotNull Module module) {
    JavaCompilerConfigurationProxy[] extensions = EP_NAME.getExtensions();
    if (extensions.length == 0) return Collections.emptyList();
    return extensions[0].getAdditionalOptionsImpl(project, module);
  }

  public static void setAdditionalOptions(@NotNull Project project, @NotNull Module module, @NotNull List<String> options) {
    JavaCompilerConfigurationProxy[] extensions = EP_NAME.getExtensions();
    if (extensions.length == 0) return;
    extensions[0].setAdditionalOptionsImpl(project, module, options);
  }
}
