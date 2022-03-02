// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated This API proved to be rather inconvenient
 * @see DataProvider
 */
@Deprecated(forRemoval = true)
public interface TypeSafeDataProvider {
  void calcData(@NotNull DataKey key, @NotNull DataSink sink);
}
