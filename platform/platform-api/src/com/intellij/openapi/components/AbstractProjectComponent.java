// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import com.intellij.openapi.project.Project;

/**
 * @deprecated Components are deprecated, please see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-components.html">SDK Docs</a> for guidelines on migrating to other APIs.
 */
@Deprecated(forRemoval = true)
public abstract class AbstractProjectComponent implements ProjectComponent {
  protected final Project myProject;

  protected AbstractProjectComponent(Project project) {
    myProject = project;
  }
}
