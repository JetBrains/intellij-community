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
                                                     byte frameworkId) {
    try {
      return TestDiscoveryIndex.getInstance(project).getTestsByMethodName(classFQName, methodName, frameworkId);
    }
    catch (IOException e) {
      TestDiscoveryProducer.LOG.error(e);
      return MultiMap.empty();
    }
  }

  @Override
  public boolean isRemote() {
    return false;
  }
}
