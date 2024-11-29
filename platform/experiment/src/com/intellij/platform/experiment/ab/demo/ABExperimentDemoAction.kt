// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.demo

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.experiment.ab.impl.experiment.ABExperiment
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentImpl

internal class ABExperimentDemoAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val service = ApplicationManager.getApplication().service<ABExperiment>() as ABExperimentImpl

    println("User experiment option is: " + service.getUserExperimentOption())
    println("User experiment option id is: " + service.getUserExperimentOptionId())
    println("Is control experiment option enabled: " + service.isControlExperimentOptionEnabled())
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}