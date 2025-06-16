// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.usages.UsageView.USAGE_INFO_KEY;

final class UsageInfo2ListRule {
  static @Nullable List<UsageInfo> getData(@NotNull DataMap dataProvider) {
    UsageInfo usageInfo = dataProvider.get(USAGE_INFO_KEY);
    if (usageInfo != null) return Collections.singletonList(usageInfo);
    return null;
  }
}
