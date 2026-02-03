// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.roots.ProjectModelBuildableElement;
import com.intellij.task.ProjectModelBuildTask;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public class ProjectModelBuildTaskImpl<T extends ProjectModelBuildableElement> extends AbstractBuildTask
  implements ProjectModelBuildTask<T> {
  private final T myBuildableElement;

  public ProjectModelBuildTaskImpl(T buildableElement, boolean isIncrementalBuild) {
    super(isIncrementalBuild);
    myBuildableElement = buildableElement;
  }

  @Override
  public T getBuildableElement() {
    return myBuildableElement;
  }

  @Override
  public @NotNull String getPresentableName() {
    return LangBundle.message("project.task.name.project.model.element.0.build.task", myBuildableElement);
  }
}
