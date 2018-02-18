// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

@ApiStatus.Experimental
public interface TestDiscoveryProducer {
  Logger LOG = Logger.getInstance(LocalTestDiscoveryProducer.class);

  class DiscoveredTest {
    private final String myTestClassQName;
    private final String myTestMethodName;

    public DiscoveredTest(String testClassQName, String testMethodName) {
      myTestClassQName = testClassQName;
      myTestMethodName = testMethodName;
    }

    public String getTestClassQName() {
      return myTestClassQName;
    }

    public String getTestMethodName() {
      return myTestMethodName;
    }
  }

  @NotNull
  List<DiscoveredTest> getDiscoveredTests(@NotNull Project project,
                                          @NotNull String classFQName,
                                          @NotNull String methodName,
                                          @NotNull String frameworkId);

  ExtensionPointName<TestDiscoveryProducer> EP = ExtensionPointName.create("com.intellij.testDiscoveryProducer");

  static void consumeDiscoveredTests(@NotNull Project project,
                                     @NotNull String classFQName,
                                     @NotNull String methodName,
                                     @NotNull String frameworkId,
                                     @NotNull Consumer<DiscoveredTest> consumer) {
    for (TestDiscoveryProducer producer : EP.getExtensions()) {
      producer.getDiscoveredTests(project, classFQName, methodName, frameworkId).forEach(consumer);
    }
  }
}
