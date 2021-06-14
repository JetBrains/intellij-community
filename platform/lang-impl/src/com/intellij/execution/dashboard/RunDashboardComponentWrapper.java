// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.openapi.util.Key;
import com.intellij.ui.components.panels.NonOpaquePanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RunDashboardComponentWrapper extends NonOpaquePanel {
  public static final Key<Integer> CONTENT_ID_KEY = Key.create("RunDashboardContentId");

  private final Integer myContentId;

  public RunDashboardComponentWrapper(JComponent component, @Nullable Integer contentId) {
    super(new BorderLayout());
    add(component, BorderLayout.CENTER);
    myContentId = contentId;
  }

  public @Nullable Integer getContentId() {
    return myContentId;
  }
}
