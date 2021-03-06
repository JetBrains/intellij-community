// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 */
public interface GetDataRule {
  ExtensionPointName<KeyedLazyInstance<GetDataRule>> EP_NAME = new ExtensionPointName<>("com.intellij.getDataRule");

  @Nullable
  Object getData(@NotNull DataProvider dataProvider);
}
