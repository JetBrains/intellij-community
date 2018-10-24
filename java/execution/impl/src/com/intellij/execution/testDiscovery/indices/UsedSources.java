// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

class UsedSources {
  @NotNull
  final Map<Integer, TIntArrayList> myUsedMethods;
  @NotNull
  final Map<Integer, Void> myUsedFiles;
  UsedSources(@NotNull Map<Integer, TIntArrayList> methods, @NotNull Map<Integer, Void> files) {
    myUsedMethods = methods;
    myUsedFiles = files;
  }
}
