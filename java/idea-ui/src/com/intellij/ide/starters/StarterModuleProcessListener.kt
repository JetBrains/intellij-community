// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.Module
import com.intellij.util.messages.Topic
import java.util.*

interface StarterModuleProcessListener : EventListener {
  @JvmDefault
  fun moduleCreated(module: Module, moduleBuilder: ModuleBuilder, frameworkVersion: String?) {
  }

  @JvmDefault
  fun moduleOpened(module: Module, moduleBuilder: ModuleBuilder, frameworkVersion: String?) {
  }

  companion object {
    @JvmStatic
    val TOPIC: Topic<StarterModuleProcessListener> = Topic(StarterModuleProcessListener::class.java)
  }
}