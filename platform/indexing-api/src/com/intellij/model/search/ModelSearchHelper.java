// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.ModelReference;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public interface ModelSearchHelper {

  @NotNull
  static ModelSearchHelper getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ModelSearchHelper.class);
  }

  boolean runSearch(@NotNull ModelReferenceSearchParameters parameters, @NotNull Processor<ModelReference> processor);
}
