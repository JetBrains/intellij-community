// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface IntervalTree<T> {
  boolean processAll(@NotNull Processor<? super T> processor);

  boolean processOverlappingWith(int start, int end, @NotNull Processor<? super T> processor);

  boolean processContaining(int offset, @NotNull Processor<? super T> processor);

  boolean removeInterval(@NotNull T interval);

  boolean processOverlappingWithOutside(int start, int end, @NotNull Processor<? super T> processor);
}
