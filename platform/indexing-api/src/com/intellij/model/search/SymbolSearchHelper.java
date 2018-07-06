// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.SymbolReference;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public interface SymbolSearchHelper {

  @NotNull
  static SymbolSearchHelper getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SymbolSearchHelper.class);
  }

  boolean runSearch(@NotNull SymbolReferenceSearchParameters parameters, @NotNull Processor<? super SymbolReference> processor);
}
