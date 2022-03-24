// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.dialog

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import javax.swing.Action
import javax.swing.JComponent

fun showFeedbackSystemInfoDialog(project: Project?,
                                 systemInfoData: CommonFeedbackSystemInfoData,
                                 addSpecificRows: Panel.() -> Unit = {}
) {
  val infoPanel = panel {
    addSpecificRows()
    row(FeedbackBundle.message("dialog.created.project.system.info.panel.os.version")) {
      label(systemInfoData.osVersion) //NON-NLS
    }
    row(FeedbackBundle.message("dialog.created.project.system.info.panel.memory")) {
      label(systemInfoData.getMemorySizeForDialog()) //NON-NLS
    }
    row(FeedbackBundle.message("dialog.created.project.system.info.panel.cores")) {
      label(systemInfoData.coresNumber.toString()) //NON-NLS
    }
    row(FeedbackBundle.message("dialog.created.project.system.info.panel.app.version")) {
      cell(MultiLineLabel(systemInfoData.appVersionWithBuild)) //NON-NLS
    }
    row(FeedbackBundle.message("dialog.created.project.system.info.panel.license.evaluation")) {
      label(systemInfoData.getIsLicenseEvaluationForDialog()) //NON-NLS
    }
    row(FeedbackBundle.message("dialog.created.project.system.info.panel.license.restrictions")) {
      cell(MultiLineLabel(systemInfoData.getLicenseRestrictionsForDialog())) //NON-NLS
    }
    row(FeedbackBundle.message("dialog.created.project.system.info.panel.runtime.version")) {
      label(systemInfoData.runtimeVersion) //NON-NLS
    }
    row(FeedbackBundle.message("dialog.created.project.system.info.panel.registry")) {
      cell(MultiLineLabel(systemInfoData.getRegistryKeysForDialog())) //NON-NLS
    }
    row(FeedbackBundle.message("dialog.created.project.system.info.panel.disabled.plugins")) {
      cell(MultiLineLabel(systemInfoData.getDisabledBundledPluginsForDialog())) //NON-NLS
    }
    row(FeedbackBundle.message("dialog.created.project.system.info.panel.nonbundled.plugins")) {
      cell(MultiLineLabel(systemInfoData.getNonBundledPluginsForDialog())) //NON-NLS
    }.bottomGap(BottomGap.MEDIUM)
  }.also {
    it.border = JBEmptyBorder(10, 10, 10, 10)
  }

  val dialog = object : DialogWrapper(project) {
    init {
      init()
      title = FeedbackBundle.message("dialog.created.project.system.info.title")
    }

    override fun createCenterPanel(): JComponent = JBScrollPane(infoPanel, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
      border = JBEmptyBorder(0)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)
  }

  dialog.show()
}