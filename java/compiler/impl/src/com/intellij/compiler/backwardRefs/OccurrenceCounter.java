// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class OccurrenceCounter<T> {
  @SuppressWarnings("SSBasedInspection")
  private final Object2IntOpenHashMap<T> myOccurrenceMap;
  private T myBest;
  private int myBestOccurrences;

  OccurrenceCounter() {
    myOccurrenceMap = new Object2IntOpenHashMap<>();
  }

  void add(@NotNull T element) {
    int prevOccurrences = myOccurrenceMap.addTo(element, 1);
    if (myBest == null) {
      myBestOccurrences = 1;
      myBest = element;
    }
    else if (myBest.equals(element)) {
      myBestOccurrences++;
    }
    else {
      myBestOccurrences = prevOccurrences + 1;
      myBest = element;
    }
  }

  @Nullable T getBest() {
    return myBest;
  }

  int getBestOccurrences() {
    return myBestOccurrences;
  }
}
