// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.tree;

import com.intellij.openapi.project.Project;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import org.jetbrains.annotations.NotNull;

public final class TreeAspect implements PomModelAspect {
  public static TreeAspect getInstance(@NotNull Project project) {
    return project.getService(TreeAspect.class);
  }

  @Override
  public void update(@NotNull PomModelEvent event) {}
}
