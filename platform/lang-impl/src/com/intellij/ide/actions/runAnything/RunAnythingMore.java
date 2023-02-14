// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.lang.LangBundle;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

final class RunAnythingMore extends JLabel {

  RunAnythingMore() {
    super(LangBundle.message("run.anything.load.more.load.more"));
    setForeground(UIUtil.getLabelDisabledForeground());
    setFont(RunAnythingUtil.getTitleFont());
    setBorder(JBUI.Borders.emptyLeft(1));
  }
}
