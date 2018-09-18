// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.statusbar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleStatusBarWidgetProvider implements StatusBarWidgetProvider {
  @Nullable
  @Override
  public StatusBarWidget getWidget(@NotNull Project project) {
    return new CodeStyleStatusBarWidget(project);
  }

  @NotNull
  @Override
  public String getAnchor() {
    return StatusBar.Anchors.after(StatusBar.StandardWidgets.ENCODING_PANEL);
  }
}
