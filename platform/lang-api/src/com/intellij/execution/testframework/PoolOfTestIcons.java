// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.icons.AllIcons;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;

public interface PoolOfTestIcons {
  Icon SKIPPED_ICON = AllIcons.RunConfigurations.TestSkipped;
  Icon PASSED_ICON = AllIcons.RunConfigurations.TestPassed;
  Icon FAILED_ICON = AllIcons.RunConfigurations.TestFailed;
  Icon ERROR_ICON = AllIcons.RunConfigurations.TestError;
  Icon NOT_RAN = AllIcons.RunConfigurations.TestNotRan;
  Icon TERMINATED_ICON = AllIcons.RunConfigurations.TestTerminated;
  Icon IGNORED_ICON = AllIcons.RunConfigurations.TestIgnored;
  Icon PASSED_IGNORED = AllIcons.RunConfigurations.TestPassedIgnored;
  Icon ERROR_ICON_MARK = AllIcons.Nodes.ErrorMark;
  Icon TEAR_DOWN_FAILURE = LayeredIcon.layeredIcon(() -> new Icon[]{PASSED_ICON, ERROR_ICON_MARK});
}
