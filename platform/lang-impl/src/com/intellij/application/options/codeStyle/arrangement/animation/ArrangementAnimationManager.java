// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.animation;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.util.ui.TimerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class ArrangementAnimationManager implements ArrangementAnimationPanel.Listener, ActionListener {

  private final @NotNull Timer myTimer =
    TimerUtil.createNamedTimer("ArrangementAnimation", ArrangementConstants.ANIMATION_STEPS_TIME_GAP_MILLIS, this);

  private final @NotNull ArrangementAnimationPanel myAnimationPanel;
  private final @NotNull Callback                  myCallback;

  private boolean myFinished;

  public ArrangementAnimationManager(@NotNull ArrangementAnimationPanel panel, @NotNull Callback callback) {
    myAnimationPanel = panel;
    myCallback = callback;
    myAnimationPanel.setListener(this);
  }

  public void startAnimation() {
    myAnimationPanel.startAnimation();
    myCallback.onAnimationIteration(false);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    myTimer.stop();
    myFinished = !myAnimationPanel.nextIteration();
    myCallback.onAnimationIteration(myFinished);
  }

  @Override
  public void onPaint() {
    if (!myFinished && !myTimer.isRunning()) {
      myTimer.start();
    }
  }

  public interface Callback {
    void onAnimationIteration(boolean finished);
  }
}
