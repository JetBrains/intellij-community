// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.tree;

import com.intellij.openapi.project.Project;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.serviceContainer.NonInjectable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public final class TreeAspect implements PomModelAspect {
  public TreeAspect(@NotNull Project project) {
    this(project.getService(PomModel.class));
  }

  @NonInjectable
  public TreeAspect(@NotNull PomModel pomModel) {
    pomModel.registerAspect(TreeAspect.class, this, Collections.emptySet());
  }

  public static TreeAspect getInstance(@NotNull Project project) {
    return project.getComponent(TreeAspect.class);
  }

  @Override
  public void update(PomModelEvent event) {}
}
