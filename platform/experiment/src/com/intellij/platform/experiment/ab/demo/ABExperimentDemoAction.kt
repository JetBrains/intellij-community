// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.demo

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.experiment.ab.impl.ABExperimentOption
import com.intellij.platform.experiment.ab.impl.getUserBucketNumber
import com.intellij.platform.experiment.ab.impl.reportableName

internal class ABExperimentDemoAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val experimentValues = ABExperimentOption.entries.filter { it != ABExperimentOption.UNASSIGNED }
    for (experimentValue in experimentValues) {
      println("Experiment value: $experimentValue; reportable name: ${experimentValue.reportableName()}; user bucket: ${getUserBucketNumber()} is enabled: ${experimentValue.isEnabled()}")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}