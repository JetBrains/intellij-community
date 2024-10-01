// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NavigatableArrayRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    Navigatable element = CommonDataKeys.NAVIGATABLE.getData(dataProvider);
    return element == null ? null : new Navigatable[]{element};
  }
}
