// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NullableLazyValue;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

final class ShowMemoryDialogAction extends AnAction implements DumbAware {
  private final NullableLazyValue<Path> userOptionsFile = lazyNullable(() -> EditMemorySettingsService.getInstance().getUserOptionsFile());

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(userOptionsFile.getValue() != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    var file = userOptionsFile.getValue();
    if (file != null) {
      new EditMemorySettingsDialog(file, VMOptions.MemoryKind.HEAP, false).show();
    }
  }
}
