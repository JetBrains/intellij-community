// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.util.TipDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.FloatingDecorator;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.stream.Stream;

public final class TipOfTheDayStartupActivity implements StartupActivity.DumbAware {
  private static final long minInitialDelay = 15 * DateFormatUtil.MINUTE;
  private static final long maxInitialDelay = 45 * DateFormatUtil.MINUTE;
  private static final long userInactivityDelay = DateFormatUtil.MINUTE;

  @Override
  public void runActivity(@NotNull final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    //Random initial delay before start tracking user activity
    Alarm initializer = new Alarm(project);
    initializer.addRequest(() -> initActivityChecker(project),
                           (minInitialDelay + Math.round(Math.random() * (maxInitialDelay - minInitialDelay))), ModalityState.any());
  }

  private static void initActivityChecker(@NotNull Project project) {
    if (project.isDisposed()) return;

    Alarm myAlarm = new Alarm(project);
    Runnable showTipsRunnable = () -> showTipsIfNeed(project);

    // After some period of inactivity when user don't type and don't click we try to catch a good moment to show "Tips of the Day"
    IdeEventQueue.getInstance().addActivityListener(() -> {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(showTipsRunnable, userInactivityDelay, ModalityState.NON_MODAL);
    }, project);
  }

  private static void showTipsIfNeed(@NotNull Project project) {
    if (project.isDisposed()) return;

    if (TipDialog.canBeShownAutomaticallyNow()) {
      IdeFrameImpl frame = WindowManagerEx.getInstanceEx().getFrame(project);
      if (frame != null
          && frame.isActive()//project frame has to be active now
          && Stream.of(frame.getOwnedWindows())
            .noneMatch(window -> window instanceof JDialog && !(window instanceof FloatingDecorator) && window.isShowing())//no dialogs above the frame
          && !JBPopupFactory.getInstance().isPopupActive()//no popups above the frame
          && !IdeTooltipManager.getInstance().hasCurrent()//no tooltips are shown
      ) {
        TipsOfTheDayUsagesCollector.triggerShow("automatically");
        TipDialog.showForProject(project);
      }
    }
  }
}
