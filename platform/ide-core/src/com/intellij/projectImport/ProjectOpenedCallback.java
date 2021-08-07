// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectImport;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

/**
 * Called when a new project is opened or attached as a module to the currently opened project.
 */
public interface ProjectOpenedCallback {
  void projectOpened(Project project, Module module);
}
