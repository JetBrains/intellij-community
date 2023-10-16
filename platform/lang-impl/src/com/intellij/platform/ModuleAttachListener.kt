// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.openapi.module.Module
import com.intellij.util.messages.Topic
import java.nio.file.Path

interface ModuleAttachListener {
  companion object {

    @Topic.ProjectLevel
    val TOPIC: Topic<ModuleAttachListener> = Topic(ModuleAttachListener::class.java, Topic.BroadcastDirection.NONE, true)
  }

  // todo find a better way to call suspend event handlers
  fun afterAttach(module: Module, primaryModule: Module?, imlFile: Path, tasks: MutableList<suspend () -> Unit>) {
  }

  fun beforeDetach(module: Module) {}
}