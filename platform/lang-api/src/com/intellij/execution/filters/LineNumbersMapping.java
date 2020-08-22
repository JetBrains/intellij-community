// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public interface LineNumbersMapping {
  /**
   * A mapping between lines contained in a byte code and actual source lines
   * (placed into a user data of a VirtualFile for a .class file).
   */
  Key<LineNumbersMapping> LINE_NUMBERS_MAPPING_KEY = Key.create("line.numbers.mapping.key");

  int bytecodeToSource(int line);
  int sourceToBytecode(int line);

  class ArrayBasedMapping implements LineNumbersMapping {
    private final int[] myMapping;

    public ArrayBasedMapping(int @NotNull [] mapping) {
      myMapping = mapping.clone();
    }

    @Override
    public int bytecodeToSource(int line) {
      for (int i = 0; i < myMapping.length; i += 2) {
        if (myMapping[i] == line) return myMapping[i + 1];
      }
      return -1;
    }

    @Override
    public int sourceToBytecode(int line) {
      for (int i = 0; i < myMapping.length; i += 2) {
        if (myMapping[i + 1] == line) return myMapping[i];
      }
      return -1;
    }
  }
}