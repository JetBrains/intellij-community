// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starters

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.Module
import com.intellij.util.messages.Topic
import java.util.*

interface StarterModuleProcessListener : EventListener {
  fun moduleCreated(module: Module, moduleBuilder: ModuleBuilder, frameworkVersion: String?) {
  }

  fun moduleOpened(module: Module, moduleBuilder: ModuleBuilder, frameworkVersion: String?) {
  }

  companion object {
    @JvmStatic
    val TOPIC: Topic<StarterModuleProcessListener> = Topic(StarterModuleProcessListener::class.java)
  }
}