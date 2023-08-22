// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.usages.UsageView;
import com.intellij.usageView.UsageInfo;

import java.util.Collections;

public final class UsageInfo2ListRule implements GetDataRule {
  @Override
  @Nullable
  public Object getData(@NotNull final DataProvider dataProvider) {
    UsageInfo usageInfo = (UsageInfo)dataProvider.getData(UsageView.USAGE_INFO_KEY.getName());
    if (usageInfo != null) return Collections.singletonList(usageInfo);
    return null;
  }
}
