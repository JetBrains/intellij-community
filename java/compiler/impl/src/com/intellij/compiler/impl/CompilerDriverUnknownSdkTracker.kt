// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.impl

import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.UnknownSdkCollector
import com.intellij.openapi.projectRoots.impl.UnknownSdkModalNotification
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker
import com.intellij.openapi.util.registry.Registry

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
                     modules: List<Module>
  ): UnknownSdkModalNotification.Outcome {
    if (!Registry.`is`("unknown.sdk.modal.jps")) return UnknownSdkModalNotification.getInstance(project).noSettingsDialogSuggested

    val collector = object: UnknownSdkCollector(project) {
      override fun checkProjectSdk(project: Project): Boolean = updateProjectSdk
      override fun collectModulesToCheckSdk(project: Project) = modules
    }

    val moduleNames = modules.map { JavaCompilerBundle.message("dialog.message.error.jdk.not.specified.with.module.name.quoted", it.name) }.toSortedSet()
    val errorMessage = JavaCompilerBundle.message("dialog.title.error.jdk.not.specified.with.fixSuggestion")
    val errorText = JavaCompilerBundle.message("dialog.message.error.jdk.not.specified.with.fixSuggestion", moduleNames.size, NlsMessages.formatAndList(moduleNames))

    val handler = UnknownSdkModalNotification
      .getInstance(project)
      .newModalHandler(errorMessage, errorText)

    UnknownSdkTracker.getInstance(project).collectUnknownSdksBlocking(collector, handler)

    return handler.outcome
  }
}
