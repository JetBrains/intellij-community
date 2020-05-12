// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class UsedSources {
  @NotNull
  final Int2ObjectOpenHashMap<IntArrayList> myUsedMethods;
  @NotNull
  final Map<Integer, Void> myUsedFiles;

  UsedSources(@NotNull Int2ObjectOpenHashMap<IntArrayList> methods, @NotNull Map<Integer, Void> files) {
    myUsedMethods = methods;
    myUsedFiles = files;
  }
}
