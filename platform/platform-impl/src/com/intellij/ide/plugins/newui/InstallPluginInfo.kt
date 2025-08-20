// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.newui.MyPluginModel.Companion.finishInstallation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.ex.StatusBarEx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
class InstallPluginInfo(
  val indicator: BgProgressIndicator,
  val descriptor: PluginUiModel,
  pluginModel: MyPluginModel,
  val install: Boolean,
) {
  private val mutex = Mutex()
  private var myPluginModel: MyPluginModel? = pluginModel
  private var myStatusBarTaskInfo: TaskInfo? = null
  private var myClosed = false

  /**
   * Descriptor that has been loaded synchronously.
   */
  private var myInstalledDescriptor: PluginUiModel? = null

  @Synchronized
  fun toBackground(statusBar: StatusBarEx?) {
    if (myPluginModel == null) return  // IDEA-355719 TODO add lifecycle assertions

    myPluginModel = null
    indicator.removeStateDelegates()

    statusBar?.let {
      val title = if (install) {
        IdeBundle.message("dialog.title.installing.plugin", descriptor.name)
      }
      else {
        IdeBundle.message("dialog.title.updating.plugin", descriptor.name)
      }
      val taskInfo = OneLineProgressIndicator.task(title)
      myStatusBarTaskInfo = taskInfo
      it.addProgress(indicator, taskInfo)
    }
  }

  @Synchronized
  fun fromBackground(pluginModel: MyPluginModel) {
    myPluginModel = pluginModel
    ourShowRestart = false
    closeStatusBarIndicator()
  }

  suspend fun finish(
    success: Boolean, cancel: Boolean, showErrors: Boolean, restartRequired: Boolean,
    errors: Map<PluginId, List<HtmlChunk>>,
  ) {
    mutex.withLock {
      if (myClosed) return
      if (myPluginModel == null) {
        finishInstallation(descriptor)
        closeStatusBarIndicator()

        if (success && !cancel && restartRequired) {
          ourShowRestart = true
        }

        if (MyPluginModel.myInstallingInfos.isEmpty() && ourShowRestart) {
          ourShowRestart = false
          withContext(Dispatchers.EDT) {
            PluginManagerConfigurable.shutdownOrRestartApp()
          }
        }
      }
      else if (!cancel) {
        myPluginModel?.finishInstall(descriptor, myInstalledDescriptor, errors, success, showErrors, restartRequired)
      }
    }
  }

  private fun closeStatusBarIndicator() {
    myStatusBarTaskInfo?.let {
      indicator.finish(it)
      myStatusBarTaskInfo = null
    }
  }

  fun close() {
    myClosed = true
  }

  fun setInstalledModel(model: PluginUiModel?) {
    myInstalledDescriptor = model
  }

  companion object {
    private var ourShowRestart = false

    fun showRestart() {
      ourShowRestart = true
    }
  }
}