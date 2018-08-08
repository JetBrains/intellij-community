// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.project.Project;

/**
 * @author Dmitry Avdeev
 */
public abstract class AbstractProjectComponent implements ProjectComponent {
  protected final Project myProject;

  protected AbstractProjectComponent(Project project) {
    myProject = project;
  }
}
