// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  void consume(@NotNull Map<String, ? extends Tools> tools,
               @NotNull List<? extends File> inspectionsResults,
               @NotNull Project project);
}

final class InspectResultsConsumerEP {
  private static final ExtensionPointName<InspectResultsConsumer> EP_NAME =
    ExtensionPointName.create("com.intellij.inspectResultsConsumer");

  static void runConsumers(@NotNull Map<String, ? extends Tools> tools,
                           @NotNull List<? extends File> inspectionsResults,
                           @NotNull Project project) {
    for (InspectResultsConsumer extension : EP_NAME.getExtensionList()) {
      extension.consume(tools, inspectionsResults, project);
    }
  }

  private InspectResultsConsumerEP() { }
}