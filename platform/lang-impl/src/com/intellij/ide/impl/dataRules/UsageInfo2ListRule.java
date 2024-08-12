// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public final class UsageInfo2ListRule implements GetDataRule {
  @Override
  public @Nullable Object getData(final @NotNull DataProvider dataProvider) {
    UsageInfo usageInfo = (UsageInfo)dataProvider.getData(UsageView.USAGE_INFO_KEY.getName());
    if (usageInfo != null) return Collections.singletonList(usageInfo);
    return null;
  }
}
