// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface ProjectSearchService {

  @NotNull
  static ProjectSearchService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectSearchService.class);
  }

  @NotNull
  SearchWordParameters.Builder searchWord(@NotNull String word);
}
