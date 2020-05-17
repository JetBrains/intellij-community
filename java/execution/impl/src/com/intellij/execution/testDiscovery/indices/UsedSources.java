// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

final class UsedSources {
  @NotNull
  final Int2ObjectMap<IntList> myUsedMethods;
  @NotNull
  final Int2ObjectMap<Void> myUsedFiles;

  UsedSources(@NotNull Int2ObjectMap<IntList> methods, @NotNull Int2ObjectMap<Void> files) {
    myUsedMethods = methods;
    myUsedFiles = files;
  }
}
