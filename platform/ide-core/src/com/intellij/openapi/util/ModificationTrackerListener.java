// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author Gregory.Shrago
 */
public interface ModificationTrackerListener<T> extends EventListener {
  void modificationCountChanged(@NotNull T source);
}
