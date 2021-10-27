// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.Tools;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public interface InspectResultsConsumer {
  ExtensionPointName<InspectResultsConsumer> EP_NAME =
    ExtensionPointName.create("com.intellij.inspectResultsConsumer");

  void consume(@NotNull Map<String, Tools> tools,
               @NotNull List<? extends File> inspectionsResults,
               @NotNull Project project);

  static void runConsumers(@NotNull Map<String, Tools> tools,
                           @NotNull List<? extends File> inspectionsResults,
                           @NotNull Project project) {
    for (InspectResultsConsumer extension : EP_NAME.getExtensions()) {
      extension.consume(tools, inspectionsResults, project);
    }
  }
}