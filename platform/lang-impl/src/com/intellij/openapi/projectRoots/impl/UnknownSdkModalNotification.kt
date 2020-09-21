// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.htmlComponent
import com.intellij.ui.layout.*
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

@Service
class UnknownSdkModalNotification(
  private val project: Project
) {
  companion object {
    private val LOG = logger<UnknownSdkModalNotification>()

    @JvmStatic
    fun getInstance(project: Project) = project.service<UnknownSdkModalNotification>()
  }

  enum class Outcome {
    NO_CHANGES,
    CONFIGURED
  }

  fun handleNotification() = object : UnknownSdkTracker.ShowStatusCallbackAdapter(project) {
    override fun notifySdks(fixed: UnknownSdkBalloonNotification.FixedSdkNotification?, actions: UnknownSdkEditorNotification.FixableSdkNotifications) = invokeLater {
      if (fixed != null) {
        mySdkBalloonNotification.notifyFixedSdks(fixed)
      }

      object : DialogWrapper(project, true) {
        init {
          title = "Resolve Missing SDKs"
          init()
        }

        override fun createCenterPanel(): JComponent {
          return panel {
            for (info in actions.infos) {
              val control = info.createNotificationPanel(project)
              row {
                val wrap = JPanel(BorderLayout())
                wrap.add(control, BorderLayout.CENTER)
                wrap.minimumSize = control.preferredSize
                wrap(CCFlags.grow)
              }
              row {
                htmlComponent(control.text)
              }
              val intentionAction = control.intentionAction
              if (intentionAction != null) {
                row {
                  link(intentionAction.text, action = {TODO()})
                }
              }
            }
          }
        }
      }.showAndGet()
    }
  }
}
