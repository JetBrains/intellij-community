// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions;

import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.ExtendableAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;

/**
 * Default implementation is in {@link com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction}.
 */
@ApiStatus.Internal
public class ShowDiffAction extends ExtendableAction implements DumbAware {
  private static final ExtensionPointName<AnActionExtensionProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.diff.actions.ShowDiffAction.ExtensionProvider");

  public ShowDiffAction() {
    super(EP_NAME);
  }
}
