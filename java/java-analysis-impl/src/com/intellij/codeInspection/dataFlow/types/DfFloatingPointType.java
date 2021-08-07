// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

public interface DfFloatingPointType extends DfPrimitiveType {
  @Override
  default boolean hasNonStandardEquivalence() {
    return true;
  }
}
