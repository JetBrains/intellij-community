// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.icon

import com.intellij.icons.AllIcons
import com.intellij.ui.ExperimentalUI
import javax.swing.Icon

object CIBuildStatusIcons {
  val pending: Icon
    get() = AllIcons.RunConfigurations.TestNotRan

  val cancelled: Icon
    get() = AllIcons.RunConfigurations.TestIgnored

  val inProgress: Icon
    get() = AllIcons.Process.Step_1

  val failed: Icon
    get() = AllIcons.RunConfigurations.TestError

  val failedInProgress: Icon
    get() = AllIcons.Status.FailedInProgress

  val warning: Icon
    get() = AllIcons.General.Warning

  val skipped: Icon
    get() = AllIcons.RunConfigurations.TestSkipped

  val paused: Icon
    get() = AllIcons.RunConfigurations.TestPaused

  val info: Icon
    get() = if (ExperimentalUI.isNewUI()) AllIcons.General.Information else AllIcons.RunConfigurations.TestPassed

  val success: Icon
    get() = if (ExperimentalUI.isNewUI()) AllIcons.Status.Success else AllIcons.RunConfigurations.TestPassed
}