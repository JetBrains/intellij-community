// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LocalTestDiscoveryProducer implements TestDiscoveryProducer {
  @Override
  @NotNull
  public MultiMap<String, String> getDiscoveredTests(@NotNull Project project,
                                                     @NotNull String classFQName,
                                                     @NotNull String methodName,
                                                     @NotNull String frameworkId) {
    try {
      TestDiscoveryIndex discoveryIndex = TestDiscoveryIndex.getInstance(project);
      Collection<String> testsByMethodName = discoveryIndex.getTestsByMethodName(classFQName, methodName, frameworkId);
      if (testsByMethodName != null) {
        // todo[batkovich]: filter by framework id
        MultiMap<String, String> result = new MultiMap<>();
        testsByMethodName.stream()
                         .map(pattern -> StringUtil.split(pattern, "-")).filter(split -> split.size() == 2)
                         .forEach(split -> result.putValue(split.get(0), split.get(1)));
        return result;
      }
    }
    catch (IOException io) {
      TestDiscoveryProducer.LOG.warn(io);
    }
    return MultiMap.empty();
  }
}
