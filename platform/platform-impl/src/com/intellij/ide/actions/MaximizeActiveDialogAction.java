// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.MaximizeDialogKt;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public final class MaximizeActiveDialogAction extends WindowAction {
  @Override
  protected @Nullable Icon getIconFor(@Nullable Window window) {
    if (!(window instanceof JDialog dialog)) return null;
    return canBeMaximized(dialog) ? AllIcons.Windows.Maximize : AllIcons.Windows.Restore;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    @Nullable Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    Window window = ComponentUtil.getWindow(component);
    if (!(window instanceof JDialog)) return;
    doMaximize((JDialog)window);
  }

  public static void doMaximize(JDialog dialog) {
    MaximizeDialogKt.toggleMaximized(dialog);
  }

  public static boolean canBeMaximized(JDialog dialog) {
    return MaximizeDialogKt.canBeMaximized(dialog);
  }

  public static void maximize(JDialog dialog) {
    MaximizeDialogKt.maximize(dialog);
  }

  public static boolean canBeNormalized(JDialog dialog) {
    return MaximizeDialogKt.canBeNormalized(dialog);
  }

  public static void normalize(JDialog dialog) {
    MaximizeDialogKt.normalize(dialog);
  }
}
