// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.installPlugins

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import java.util.*
import java.util.function.Consumer

class TransferSettingsDownloadPluginsTask(project: Project, private val plugins: List<PluginNode>,
                                          private val customPlugins: Collection<PluginNode>,
                                          private val allowInstallWithoutRestart: Boolean,
                                          private val pluginEnabler: PluginEnabler,
                                          private val modalityState: ModalityState,
                                          private val function: Consumer<in Boolean>?) : Task.Backgroundable(project, IdeBundle.message("progress.download.plugins"), true) {
  override fun run(indicator: ProgressIndicator) {
    var success = true
    try {
      val operation = PluginInstallOperation(plugins, customPlugins, pluginEnabler, indicator)
      operation.setAllowInstallWithoutRestart(allowInstallWithoutRestart)
      operation.run()
      success = operation.isSuccess
      if (success) {
        ApplicationManager.getApplication().invokeLater(Runnable {
          if (allowInstallWithoutRestart) {
            for ((file, pluginDescriptor) in operation.pendingDynamicPluginInstalls) {
              success = success and PluginInstaller.installAndLoadDynamicPlugin(file, pluginDescriptor)
            }
          }
        }, modalityState)
      }
    }
    finally {
      ApplicationManager.getApplication().invokeLater({ function?.accept(success) }, ModalityState.current())
    }
  }
}