// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.*;

@ApiStatus.Internal
public final class SimplePushAction extends PushActionBase {
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
    dialog.push(false);
  }
}
