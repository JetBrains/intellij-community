// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * A project level disposable for the Java plugin.
 * Use this class instead of passing [Project] as a disposable to make sure your resource gets disposed when the Java plugin is unloaded.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class JavaPluginDisposable(val coroutineScope: CoroutineScope) : Disposable {
  override fun dispose() { }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): JavaPluginDisposable = project.service<JavaPluginDisposable>()
  }
}