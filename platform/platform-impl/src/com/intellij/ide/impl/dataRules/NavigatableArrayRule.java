// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NavigatableArrayRule {
  static Navigatable @Nullable [] getData(@NotNull DataMap dataProvider) {
    Navigatable element = dataProvider.get(CommonDataKeys.NAVIGATABLE);
    return element == null ? null : new Navigatable[]{element};
  }
}
