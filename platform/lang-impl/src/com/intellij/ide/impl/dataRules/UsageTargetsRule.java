// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.usages.UsageTargetUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UsageTargetsRule implements GetDataRule {
  @Override
  @Nullable
  public Object getData(@NotNull DataProvider dataProvider) {
    return UsageTargetUtil.findUsageTargets(dataProvider);
  }
}
