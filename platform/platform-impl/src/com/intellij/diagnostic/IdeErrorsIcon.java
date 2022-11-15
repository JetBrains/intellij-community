// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnimatedIcon.Blinking;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

import static com.intellij.util.ui.EmptyIcon.ICON_16;

final class IdeErrorsIcon extends JLabel {
  private static final int TIMEOUT = (int)TimeUnit.SECONDS.toMillis(Registry.intValue("ea.indicator.blinking.timeout", -1));

  private final boolean myEnableBlink;
  private final @Nullable MergingUpdateQueue myBlinkTimeoutQueue;

  IdeErrorsIcon(boolean canBlink) {
    myEnableBlink = canBlink && TIMEOUT != 0;
    myBlinkTimeoutQueue =
      myEnableBlink && TIMEOUT > 0 ? new MergingUpdateQueue("ide-error-icon-blink-timeout", TIMEOUT, true, null).setRestartTimerOnAdd(true) : null;
  }

  void setState(@NotNull MessagePool.State state) {
  }
}
