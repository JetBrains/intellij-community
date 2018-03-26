// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

@ApiStatus.Experimental
public interface TestDiscoveryProducer {
  Logger LOG = Logger.getInstance(LocalTestDiscoveryProducer.class);

  @NotNull
  MultiMap<String, String> getDiscoveredTests(@NotNull Project project,
                                              @NotNull String classFQName,
                                              @NotNull String methodName,
                                              byte frameworkId);

  boolean isRemote();

  ExtensionPointName<TestDiscoveryProducer> EP = ExtensionPointName.create("com.intellij.testDiscoveryProducer");

  static void consumeDiscoveredTests(@NotNull Project project,
                                     @NotNull String classFQName,
                                     @NotNull String methodName,
                                     byte frameworkId,
                                     @NotNull BiConsumer<String, String> consumer) {
    MultiMap<String, String> visitedTests = new MultiMap<String, String>() {
      @NotNull
      @Override
      protected Collection<String> createCollection() {
        return new THashSet<>();
      }
    };
    for (TestDiscoveryProducer producer : EP.getExtensions()) {
      for (Map.Entry<String, Collection<String>> entry : producer.getDiscoveredTests(project, classFQName, methodName, frameworkId)
                                                                 .entrySet()) {
        String cName = entry.getKey();
        for (String mName : entry.getValue()) {
          if (!visitedTests.get(classFQName).contains(mName)) {
            visitedTests.putValue(cName, mName);
            consumer.accept(cName, mName);
          }
        }
      }
    }
  }
}
