// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class LocalTestDiscoveryProducer implements TestDiscoveryProducer {
  @Override
  @NotNull
  public MultiMap<String, String> getDiscoveredTests(@NotNull Project project,
                                                     @NotNull List<Couple<String>> classesAndMethods,
                                                     byte frameworkId) {
    MultiMap<String, String> result = new MultiMap<>();
    TestDiscoveryIndex instance = TestDiscoveryIndex.getInstance(project);
    classesAndMethods.forEach(couple -> result.putAllValues(couple.second == null ?
                                                            instance.getTestsByClassName(couple.first, frameworkId) :
                                                            instance.getTestsByMethodName(couple.first, couple.second, frameworkId)));
    return result;
  }

  @NotNull
  @Override
  public MultiMap<String, String> getDiscoveredTestsForFiles(@NotNull Project project, @NotNull List<String> paths, byte frameworkId) {
    MultiMap<String, String> result = new MultiMap<>();
    TestDiscoveryIndex instance = TestDiscoveryIndex.getInstance(project);
    for (String path : paths) {
      result.putAllValues(instance.getTestsByFile(path, frameworkId));
    }
    return result;
  }

  @NotNull
  @Override
  public List<String> getAffectedFilePaths(@NotNull Project project, @NotNull List<String> testFqns, byte frameworkId) {
    return Collections.emptyList();
  }

  @Override
  public boolean isRemote() {
    return false;
  }
}
