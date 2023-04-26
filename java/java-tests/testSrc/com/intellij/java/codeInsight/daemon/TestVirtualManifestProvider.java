// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.analysis.VirtualManifestProvider;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class TestVirtualManifestProvider implements VirtualManifestProvider {
  private final Map<String, Map<String, String>> myAttributeMaps;

  public TestVirtualManifestProvider(Map<String, Map<String, String>> attributeMaps) {
    myAttributeMaps = attributeMaps;
  }

  @Override
  public String getValue(@NotNull Module module, @NotNull String attribute) {
    Map<String, String> attributeMap = myAttributeMaps.get(module.getName());
    if (attributeMap == null) return null;
    return attributeMap.get(attribute);
  }
}