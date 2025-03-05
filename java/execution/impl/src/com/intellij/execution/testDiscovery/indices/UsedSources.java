// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery.indices;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

final class UsedSources {
  final @NotNull Int2ObjectMap<IntList> myUsedMethods;
  final @NotNull Int2ObjectMap<Void> myUsedFiles;

  UsedSources(@NotNull Int2ObjectMap<IntList> methods, @NotNull Int2ObjectMap<Void> files) {
    myUsedMethods = methods;
    myUsedFiles = files;
  }
}
