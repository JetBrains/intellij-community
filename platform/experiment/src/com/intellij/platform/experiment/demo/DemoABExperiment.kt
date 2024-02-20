// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.demo

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.experiment.ab.ABExperiment
import com.intellij.platform.experiment.ab.ABExperimentConfigBase

fun getInstance(): DemoABExperiment {
  return ApplicationManager.getApplication().service<DemoABExperiment>()
}


@Service
class DemoABExperiment : ABExperiment<ABExperimentConfigBase>() {

  override val id: String = "demo.ab_experiment"

  override val experimentConfig: ABExperimentConfigBase = object : ABExperimentConfigBase() {}

  override val testRegistryKey: String = "demo.ab_experiment"

}