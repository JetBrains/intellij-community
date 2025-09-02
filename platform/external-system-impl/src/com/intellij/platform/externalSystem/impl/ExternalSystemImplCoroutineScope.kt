// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl

import com.intellij.openapi.application.Application
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal object ExternalSystemImplCoroutineScope {

  @Service(Service.Level.APP)
  private class ApplicationService(val coroutineScope: CoroutineScope)

  @Service(Service.Level.PROJECT)
  private class ProjectService(val coroutineScope: CoroutineScope)

  val Application.esCoroutineScope: CoroutineScope
    get() = service<ApplicationService>().coroutineScope

  val Project.esCoroutineScope: CoroutineScope
    get() = service<ProjectService>().coroutineScope
}