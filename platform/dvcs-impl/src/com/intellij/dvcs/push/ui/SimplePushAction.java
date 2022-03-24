// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

@ApiStatus.Internal
public final class SimplePushAction extends PushActionBase {

  private Predicate<VcsPushUi> condition;

  SimplePushAction() {
    super(DvcsBundle.message("action.complex.push"));
  }

  @Override
  protected boolean isEnabled(@NotNull VcsPushUi dialog) {
    return dialog.canPush();
  }

  @Override
  protected @Nls @Nullable String getDescription(@NotNull VcsPushUi dialog, boolean enabled) {
    return null;
  }

  @Override
  protected void actionPerformed(@NotNull Project project, @NotNull VcsPushUi dialog) {
    if (condition == null || condition.test(dialog)) {
      dialog.push(false);
    }
  }

  void setCondition(@Nullable Predicate<VcsPushUi> condition) {
    this.condition = condition;
  }
}
