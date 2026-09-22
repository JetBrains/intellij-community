// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.GotItComponentBuilder;
import com.intellij.ui.GotItTooltip;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.awt.Point;
import java.util.concurrent.TimeUnit;

public final class RerunTestsNotification {

  private static final @NonNls String TOOLTIP_ID = "rerun.tests";

  public static void showRerunNotification(@Nullable RunContentDescriptor contentToReuse,
                                           @NotNull ExecutionConsole executionConsole) {
    if (contentToReuse == null) {
      return;
    }
    String lastActionId = ActionManagerEx.getInstanceEx().getPrevPreformedActionId();
    if (RerunTestsAction.ID.equals(lastActionId)) {
      return;
    }
    String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(RerunTestsAction.ID));
    if (shortcutText.isEmpty()) {
      return;
    }
    GotItTooltip tooltip = new GotItTooltip(TOOLTIP_ID, ExecutionBundle.message("popup.content.rerun.tests.with", shortcutText), executionConsole)
      .withPosition(Balloon.Position.above);
    if (!tooltip.canShow()) {
      Disposer.dispose(tooltip);
      return;
    }
    CheckedDisposable lifetime = Disposer.newCheckedDisposable();
    Disposer.register(tooltip, lifetime);
    UiNotifyConnector.doWhenFirstShown(executionConsole.getComponent(), () -> {
      EdtExecutorService.getScheduledExecutorInstance()
        .schedule(() -> showTooltip(tooltip, lifetime, executionConsole), 1000, TimeUnit.MILLISECONDS);
    }, tooltip);
  }

  private static void showTooltip(@NotNull GotItTooltip tooltip,
                                  @NotNull CheckedDisposable lifetime,
                                  @NotNull ExecutionConsole executionConsole) {
    if (lifetime.isDisposed()) {
      return;
    }
    ConsoleView consoleView = UIUtil.findComponentOfType(executionConsole.getComponent(), ConsoleViewImpl.class);
    if (consoleView == null) {
      Disposer.dispose(tooltip);
      return;
    }
    tooltip.show(consoleView.getComponent(), RerunTestsNotification::getBottomRightPoint);
  }

  /**
   * Returns the pointer target that keeps the balloon inside the bottom-right corner of the component.
   */
  private static @NotNull Point getBottomRightPoint(@NotNull Component component, @NotNull Balloon balloon) {
    int inset = JBUIScale.scale(12);
    int x = component.getWidth() - inset - balloon.getPreferredSize().width + GotItComponentBuilder.getArrowShift();
    return new Point(Math.max(inset, x), component.getHeight() - inset);
  }
}
