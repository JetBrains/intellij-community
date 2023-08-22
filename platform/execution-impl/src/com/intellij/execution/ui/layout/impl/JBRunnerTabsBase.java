// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui.layout.impl;

import com.intellij.ui.tabs.JBTabsEx;

import java.awt.*;


public interface JBRunnerTabsBase extends JBTabsEx {
  boolean shouldAddToGlobal(Point point);
}
