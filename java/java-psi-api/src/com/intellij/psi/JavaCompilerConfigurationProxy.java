// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import java.util.Collections;
import java.util.List;

public abstract class JavaCompilerConfigurationProxy {
  private static ExtensionPointName<JavaCompilerConfigurationProxy> EP_NAME = new ExtensionPointName<>("com.intellij.javaCompilerConfigurationProxy");

  abstract protected List<String> getAdditionalOptionsImpl(Project project, Module module);
  abstract protected void setAdditionalOptionsImpl(Project project, Module module, List<String> options);

  public static List<String> getAdditionalOptions(Project project, Module module) {
    JavaCompilerConfigurationProxy[] extensions = EP_NAME.getExtensions();
    if (extensions.length == 0) return Collections.emptyList();
    return extensions[0].getAdditionalOptionsImpl(project, module);
  }

  public static void setAdditionalOptions(Project project, Module module, List<String> options) {
    JavaCompilerConfigurationProxy[] extensions = EP_NAME.getExtensions();
    if (extensions.length == 0) return;
    extensions[0].setAdditionalOptionsImpl(project, module, options);
  }
}
