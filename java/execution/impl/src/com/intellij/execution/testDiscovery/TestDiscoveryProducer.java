// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.function.BiConsumer;

@ApiStatus.Experimental
public interface TestDiscoveryProducer {
  Logger LOG = Logger.getInstance(LocalTestDiscoveryProducer.class);

  @NotNull
  Map<String, String> getTestClassesAndMethodNames(Project project,
                                                   String classFQName,
                                                   String methodName,
                                                   String frameworkId);

  ExtensionPointName<TestDiscoveryProducer> EP = ExtensionPointName.create("com.intellij.testDiscoveryProducer");

  static void consumeTestClassesAndMethods(@NotNull Project project,
                                           @NotNull String classFQName,
                                           @NotNull String methodName,
                                           @NotNull String frameworkId,
                                           @NotNull BiConsumer<String, String> consumer) {
    for (TestDiscoveryProducer producer : EP.getExtensions()) {
      producer.getTestClassesAndMethodNames(project, classFQName, methodName, frameworkId).forEach(consumer);
    }
  }
}
