// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@ApiStatus.Experimental
public interface TestDiscoveryProducer {
  ExtensionPointName<TestDiscoveryProducer> EP = ExtensionPointName.create("com.intellij.testDiscoveryProducer");

  Logger LOG = Logger.getInstance(LocalTestDiscoveryProducer.class);

  @NotNull
  MultiMap<String, String> getDiscoveredTests(@NotNull Project project,
                                              @NotNull List<Couple<String>> classesAndMethods,
                                              byte frameworkId);

  @NotNull
  MultiMap<String, String> getDiscoveredTestsForFiles(@NotNull Project project,
                                                      @NotNull List<String> paths,
                                                      byte frameworkId);

  boolean isRemote();

  static void consumeDiscoveredTests(@NotNull Project project,
                                     @NotNull List<Couple<String>> classesAndMethods,
                                     byte frameworkId,
                                     @NotNull List<String> filePaths,
                                     @NotNull TestProcessor processor) {
    MultiMap<String, String> visitedTests = new MultiMap<String, String>() {
      @NotNull
      @Override
      protected Collection<String> createCollection() {
        return new THashSet<>();
      }
    };
    for (TestDiscoveryProducer producer : EP.getExtensions()) {
      for (Map.Entry<String, Collection<String>> entry : ContainerUtil.concat(
        producer.getDiscoveredTests(project, classesAndMethods, frameworkId).entrySet(),
        producer.getDiscoveredTestsForFiles(project, filePaths, frameworkId).entrySet())) {
        String className = entry.getKey();
        for (String methodRawName : entry.getValue()) {
          if (!visitedTests.get(className).contains(methodRawName)) {
            visitedTests.putValue(className, methodRawName);
            Couple<String> couple = extractParameter(methodRawName);
            if (!processor.process(className, couple.first, couple.second)) return;
          }
        }
      }
    }
  }

  @NotNull
  List<String> getAffectedFilePaths(@NotNull Project project, @NotNull List<Couple<String>> testFqns, byte frameworkId) throws IOException;

  @NotNull
  List<String> getAffectedFilePathsByClassName(@NotNull Project project, @NotNull String testClassNames, byte frameworkId) throws IOException;

  @NotNull
  List<String> getFilesWithoutTests(@NotNull Project project, @NotNull Collection<String> paths) throws IOException;

  // testFqn - (className, methodName)
  static void consumeAffectedPaths(@NotNull Project project, @NotNull List<Couple<String>> testFqns, @NotNull Consumer<? super String> pathsConsumer, byte frameworkId) throws IOException {
    for (TestDiscoveryProducer extension : EP.getExtensions()) {
      for (String path : extension.getAffectedFilePaths(project, testFqns, frameworkId)) {
        pathsConsumer.consume(path);
      }
    }
  }

  static void consumeAffectedPaths(@NotNull Project project, @NotNull String testClassName, @NotNull Consumer<? super String> pathsConsumer, byte frameworkId) throws IOException {
    for (TestDiscoveryProducer extension : EP.getExtensions()) {
      for (String path : extension.getAffectedFilePathsByClassName(project, testClassName, frameworkId)) {
        pathsConsumer.consume(path);
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
  interface TestProcessor {
    boolean process(@NotNull String className, @NotNull String methodName, @Nullable String parameter);
  }

  @FunctionalInterface
  interface PsiTestProcessor {
    boolean process(@NotNull PsiClass clazz, @Nullable PsiMethod method, @Nullable String parameter);
  }
}
