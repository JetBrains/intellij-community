// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class DefaultCompactActionGroup extends DefaultActionGroup {
  public DefaultCompactActionGroup() {
    super();
  }

  public DefaultCompactActionGroup(AnAction @NotNull ... actions) {
    super(actions);
  }

  public DefaultCompactActionGroup(@NlsActions.ActionText String shortName, boolean popup) {
    super(shortName, popup);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull Presentation createTemplatePresentation() {
    Presentation presentation = super.createTemplatePresentation();
    presentation.putClientProperty(ActionUtil.HIDE_DISABLED_CHILDREN, true);
    return presentation;
  }
}
