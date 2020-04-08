// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.util.Key;

public interface LineNumbersMapping {
  /**
   * A mapping between lines contained in a byte code and actual source lines
   * (placed into a user data of a VirtualFile for a .class file).
   */
  Key<LineNumbersMapping> LINE_NUMBERS_MAPPING_KEY = Key.create("line.numbers.mapping.key");

  int bytecodeToSource(int line);
  int sourceToBytecode(int line);
}
