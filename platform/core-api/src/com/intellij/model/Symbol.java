// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.navigation.NavigationService;
import com.intellij.navigation.NavigationTarget;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface Symbol {

  boolean isValid();

  @NotNull
  default Collection<? extends NavigationTarget> getNavigationTargets(@NotNull Project project) {
    return NavigationService.getInstance(project).getNavigationTargets(this);
  }
}
