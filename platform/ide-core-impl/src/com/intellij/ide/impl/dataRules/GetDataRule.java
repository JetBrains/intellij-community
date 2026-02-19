// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides data for given {@link com.intellij.openapi.actionSystem.DataKey}.
 * <p/>
 * Must be registered with {@code key} value matching {@code DataKey#getName()}.
 *
 * @deprecated Use {@link com.intellij.openapi.actionSystem.UiDataRule} instead.
 *
 * @see com.intellij.openapi.actionSystem.DataSink#lazy
 * @see com.intellij.openapi.actionSystem.DataSink#lazyValue
 */
@Deprecated(forRemoval = true)
public interface GetDataRule {
  ExtensionPointName<KeyedLazyInstance<GetDataRule>> EP_NAME = new ExtensionPointName<>("com.intellij.getDataRule");

  @Nullable
  Object getData(@NotNull DataProvider dataProvider);
}
