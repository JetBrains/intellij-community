/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.ui.PlatformScalingUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class ScaleToScreenDpiAction extends AnAction implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!PlatformScalingUtil.getInstance().isMultiMonitorAware()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    float currentScaleFactor = PlatformScalingUtil.getInstance().getActiveScaleFactor();
    float windowScaleFactor = PlatformScalingUtil.getInstance().getScaleFactorForWindow(activeFrame);
    if (currentScaleFactor == windowScaleFactor) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setEnabledAndVisible(true);
    String text = String.format("%1$s (%2$d%% => %3$d%%)",
                                ActionsBundle.actionText("ScaleToScreenDpi"),
                                (int)(currentScaleFactor * 100.0f),
                                (int)(windowScaleFactor * 100.0f));
    e.getPresentation().setText(text);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e){
    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) {
      return;
    }

    float windowScaleFactor = PlatformScalingUtil.getInstance().getScaleFactorForWindow(activeFrame);
    WindowManagerEx.getInstanceEx().rescaleFrames(windowScaleFactor);
  }
}
