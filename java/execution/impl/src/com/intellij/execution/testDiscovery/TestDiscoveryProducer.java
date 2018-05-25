// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;

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
        String cName = entry.getKey();
        for (String mRawName : entry.getValue()) {
          if (!visitedTests.get(classFQName).contains(mRawName)) {
            visitedTests.putValue(cName, mRawName);

            String mName;
            String parameter;

            int idx = mRawName.indexOf('[');
            if (idx == -1) {
              mName = mRawName;
              parameter = null;
            } else {
              mName = mRawName.substring(0, idx);
              parameter = mRawName.substring(idx);
            }

            consumer.accept(cName, mName, parameter);
          }
        }
      }
    }
  }

  @FunctionalInterface
  interface TestConsumer {
    boolean accept(@NotNull String className, @NotNull String methodName, @Nullable String parameter);
  }
}
