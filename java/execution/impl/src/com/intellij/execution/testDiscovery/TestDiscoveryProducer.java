// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

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
                                     @NotNull TestConsumer consumer) {
    MultiMap<String, String> visitedTests = new MultiMap<String, String>() {
      @NotNull
      @Override
      protected Collection<String> createCollection() {
        return new THashSet<>();
      }
    };
    for (TestDiscoveryProducer producer : EP.getExtensions()) {
      for (Map.Entry<String, Collection<String>> entry : producer.getDiscoveredTests(project, classFQName, methodName, frameworkId).entrySet()) {
        String className = entry.getKey();
        for (String methodRawName : entry.getValue()) {
          if (!visitedTests.get(classFQName).contains(methodRawName)) {
            visitedTests.putValue(className, methodRawName);
            Couple<String> couple = extractParameter(methodRawName);
            consumer.accept(className, couple.first, couple.second);
          }
        }
      }
    }
  }

  @NotNull
  static Couple<String> extractParameter(@NotNull String rawName) {
    int idx = rawName.indexOf('[');
    return idx == -1 ?
           Couple.of(rawName, null) :
           Couple.of(rawName.substring(0, idx), rawName.substring(idx));
  }

  @FunctionalInterface
  interface TestConsumer {
    boolean accept(@NotNull String className, @NotNull String methodName, @Nullable String parameter);
  }
}
