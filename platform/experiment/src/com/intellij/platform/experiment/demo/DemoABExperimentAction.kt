// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.demo

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class DemoABExperimentAction : AnAction("Aaaaaaa test") {
  override fun actionPerformed(e: AnActionEvent) {
    println(getInstance().isExperimentEnabled())
    println(getInstance().getUserGroupKind())
    println(getInstance().getUserGroupNumber())
  }
}