// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

abstract class BranchService {
  static BranchService getInstance() {
    return ApplicationManager.getApplication().getService(BranchService.class);
  }

  abstract @NotNull ModelPatch performInBranch(@NotNull Project project, @NotNull Consumer<? super ModelBranch> action);

}
