// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.dataflow;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Semilattice<E> {
  E join(@NotNull List<E> ins);

  boolean eq(@NotNull E e1, @NotNull E e2);
}