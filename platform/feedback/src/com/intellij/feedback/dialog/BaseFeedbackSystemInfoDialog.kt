// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.dialog

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBEmptyBorder
import javax.swing.Action
import javax.swing.JComponent

fun showFeedbackSystemInfoDialog(project: Project?,
                                 systemInfoData: CommonFeedbackSystemInfoData,
                                 addSpecificRows: LayoutBuilder.() -> Unit = {}
) {
  val infoPanel = panel {
    addSpecificRows()
    row {
      cell {
        label(FeedbackBundle.message("dialog.created.project.system.info.panel.os.version"))
      }
      cell {
        label(systemInfoData.osVersion) //NON-NLS
      }
    }
    row {
      cell {
        label(FeedbackBundle.message("dialog.created.project.system.info.panel.memory"))
      }
      cell {
        label(systemInfoData.getMemorySizeForDialog()) //NON-NLS
      }
    }
    row {
      cell {
        label(FeedbackBundle.message("dialog.created.project.system.info.panel.cores"))
      }
      cell {
        label(systemInfoData.coresNumber.toString()) //NON-NLS
      }
    }
    row {
      cell {
        label(FeedbackBundle.message("dialog.created.project.system.info.panel.app.version"))
      }
      cell {
        MultiLineLabel(systemInfoData.appVersionWithBuild)() //NON-NLS
      }
    }
    row {
      cell {
        label(FeedbackBundle.message("dialog.created.project.system.info.panel.license"))
      }
      cell {
        MultiLineLabel(systemInfoData.getLicenseInfoForDialog())() //NON-NLS
      }
    }
    row {
      cell {
        label(FeedbackBundle.message("dialog.created.project.system.info.panel.runtime.version"))
      }
      cell {
        label(systemInfoData.runtimeVersion) //NON-NLS
      }
    }
    row {
      cell {
        label(FeedbackBundle.message("dialog.created.project.system.info.panel.registry"))
      }
      cell {
        MultiLineLabel(systemInfoData.getRegistryKeysForDialog())() //NON-NLS
      }
    }
    row {
      cell {
        label(FeedbackBundle.message("dialog.created.project.system.info.panel.disabled.plugins"))
      }
      cell {
        MultiLineLabel(systemInfoData.getDisabledBundledPluginsForDialog())() //NON-NLS
      }
    }
    row {
      cell {
        label(FeedbackBundle.message("dialog.created.project.system.info.panel.nonbundled.plugins"))
      }
      cell {
        MultiLineLabel(systemInfoData.getNonBundledPluginsForDialog())() //NON-NLS
      }
      largeGapAfter()
    }
  }.also {
    it.border = JBEmptyBorder(10, 10, 10, 10)
  }

  val dialog = object : DialogWrapper(project) {
    init {
      init()
      title = FeedbackBundle.message("dialog.created.project.system.info.title")
    }

    override fun createCenterPanel(): JComponent = infoPanel

    override fun createActions(): Array<Action> = arrayOf(okAction)
  }

  dialog.show()
}