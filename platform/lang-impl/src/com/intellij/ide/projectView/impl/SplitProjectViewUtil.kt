@file:ApiStatus.Internal
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.ide.productMode.IdeProductMode
import org.jetbrains.annotations.ApiStatus

fun isProjectViewSplit(): Boolean {
  return RegistryManager.getInstance().isProjectViewSplit()
}

suspend fun isProjectViewSplitAsync(): Boolean {
  return RegistryManager.getInstanceAsync().isProjectViewSplit()
}

private fun RegistryManager.isProjectViewSplit(): Boolean {
  if (IdeProductMode.isMonolith) {
    return `is`("project.view.toolwindow.split") || `is`("project.view.toolwindow.split.monolith")
  }
  else {
    return `is`("project.view.toolwindow.split") || `is`("project.view.toolwindow.split.remdev")
  }
}
