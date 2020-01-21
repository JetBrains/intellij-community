// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import org.jetbrains.annotations.Nullable;

public interface InnerClassSourceStrategy<T> {
  @Nullable
  T findInnerClass(String innerName, T outerClass);

  void accept(T innerClass, StubBuildingVisitor<T> visitor);
}