// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LocalTestDiscoveryProducer implements TestDiscoveryProducer {
  @Override
  @NotNull
  public Map<String, String> getTestClassesAndMethodNames(@NotNull Project project,
                                                          @NotNull String classFQName,
                                                          @NotNull String methodName,
                                                          @NotNull String frameworkId) {
    try {
      Map<String, String> map = ContainerUtil.newLinkedHashMap();
      TestDiscoveryIndex discoveryIndex = TestDiscoveryIndex.getInstance(project);
      Collection<String> testsByMethodName = discoveryIndex.getTestsByMethodName(classFQName, methodName, frameworkId);
      if (testsByMethodName != null) {
        for (String pattern : testsByMethodName) { // todo[batkovich]: filter by framework id
          List<String> split = StringUtil.split(pattern, "-");
          if (split.size() == 2) {
            map.put(split.get(0), split.get(1));
          }
        }
      }
      return map;
    }
    catch (IOException io) {
      TestDiscoveryProducer.LOG.warn(io);
    }
    return Collections.emptyMap();
  }
}
