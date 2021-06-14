// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FormatOnSaveActionProvider extends ActionOnSaveInfoProvider {
  @Override
  protected @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull ActionOnSaveContext context) {
    // TODO correct the supported IDE list.
    if (PlatformUtils.isIntelliJ() || PlatformUtils.isWebStorm()) {
      return List.of(new FormatOnSaveActionInfo(context));
    }
    return Collections.emptyList();
  }
}
