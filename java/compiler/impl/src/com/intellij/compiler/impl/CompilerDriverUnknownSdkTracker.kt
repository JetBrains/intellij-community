// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.UnknownSdkCollector
import com.intellij.openapi.projectRoots.impl.UnknownSdkModalNotification
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker

@Service
class CompilerDriverUnknownSdkTracker(
  private val project: Project
) {
  companion object {
    val LOG = logger<CompilerDriverUnknownSdkTracker>()

    @JvmStatic
    fun getInstance(project: Project) = project.service<CompilerDriverUnknownSdkTracker>()
  }

  fun fixSdkSettings(updateProjectSdk: Boolean,
                     modules: List<Module>) {
    val collector = object: UnknownSdkCollector(project) {
      override fun checkProjectSdk(project: Project): Boolean = updateProjectSdk
      override fun collectModulesToCheckSdk(project: Project) = modules
    }

    UnknownSdkTracker.getInstance(project).updateUnknownSdksBlocking(collector, UnknownSdkModalNotification.getInstance(project).handleNotification())
  }
}
