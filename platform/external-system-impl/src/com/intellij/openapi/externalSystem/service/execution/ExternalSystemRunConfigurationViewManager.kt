// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildProgressObservable
import com.intellij.build.BuildViewProblemsService
import com.intellij.build.ViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.containers.DisposableWrapperList

@Service(Service.Level.PROJECT)
class ExternalSystemRunConfigurationViewManager(project: Project) : ViewManager, BuildProgressListener, BuildProgressObservable {
  private val listeners = DisposableWrapperList<BuildProgressListener>()
  override fun isConsoleEnabledByDefault() = true

  override fun isBuildContentView() = false

  init {
    project.service<BuildViewProblemsService>().listenToBuildView(this)
  }

  override fun addListener(listener: BuildProgressListener, disposable: Disposable) {
    listeners.add(listener, disposable)
  }

  override fun onEvent(buildId: Any, event: BuildEvent) {
    for (listener in listeners) {
      try {
        listener.onEvent(buildId, event)
      }
      catch (e: Exception) {
        logger.warn(e)
      }
    }
  }

  companion object {
    private val logger = logger<ExternalSystemRunConfigurationViewManager>()
  }
}