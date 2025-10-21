// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push.ui

import com.intellij.dvcs.push.PushSupport
import com.intellij.dvcs.push.PushTarget
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showOkCancelDialog
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil

private class ForcePushAction : PushActionBase() {
  override fun actionPerformed(project: Project, ui: VcsPushUi) {
    if (confirmForcePush(project, ui)) {
      ui.push(true)
    }
  }

  override fun isEnabled(ui: VcsPushUi): Boolean {
    return ui.canPush() && getProhibitedTarget(ui) == null
  }

  override fun getDescription(ui: VcsPushUi, enabled: Boolean): String? {
    val prohibitedTarget = getProhibitedTarget(ui)
    val configurablePath = getProhibitedTargetConfigurablePath(ui)
    if (!enabled && prohibitedTarget != null) {
      var message = DvcsBundle.message("action.force.push.is.prohibited.description", prohibitedTarget.presentation)
      if (configurablePath != null) {
        message += UIUtil.BR + DvcsBundle.message("action.force.push.is.prohibited.settings.link", configurablePath)
      }
      return message
    }
    if (configurablePath != null) {
      return DvcsBundle.message("action.force.push.is.prohibited.settings.link", configurablePath)
    }
    return null
  }

  private fun confirmForcePush(project: Project, ui: VcsPushUi): Boolean {
    val silentForcePushIsNotAllowed = ui.selectedPushSpecs
      .mapValues { (support, pushInfos) ->
        pushInfos.filter { !support.isSilentForcePushAllowed(it.pushSpec.target) }
      }
      .filterValues { it.isNotEmpty() }

    if (silentForcePushIsNotAllowed.isEmpty()) {
      return true
    }

    // get common target if everything is pushed "synchronously" into a single place

    val commonTarget: PushTarget?
    val aSupport: PushSupport<*, *, PushTarget>?
    if (silentForcePushIsNotAllowed.size > 1) {
      aSupport = null
      commonTarget = null
    }
    else {
      aSupport = silentForcePushIsNotAllowed.keys.first()
      commonTarget = silentForcePushIsNotAllowed[aSupport]!!.map { it.pushSpec.target }.distinct().singleOrNull()
    }

    val message = if (commonTarget != null) {
      DvcsBundle.message("action.force.push.to.confirmation.text", commonTarget.presentation)
    }
    else {
      DvcsBundle.message("action.force.push.confirmation.text")
    }
    val myDoNotAskOption = if (commonTarget != null) MyDoNotAskOptionForPush(aSupport!!, commonTarget) else null
    val decision = showOkCancelDialog(title = DvcsBundle.message("force.push.dialog.title"),
                                      message = XmlStringUtil.wrapInHtml(message),
                                      okText = DvcsBundle.message("action.force.push"),
                                      icon = Messages.getWarningIcon(), doNotAskOption = myDoNotAskOption,
                                      project = project)
    return decision == Messages.OK
  }
}

private class MyDoNotAskOptionForPush(private val pushSupport: PushSupport<*, *, PushTarget>,
                                      private val commonTarget: PushTarget) : DoNotAskOption.Adapter() {
  override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
    if (exitCode == Messages.OK && isSelected) {
      pushSupport.saveSilentForcePushTarget(commonTarget)
    }
  }

  override fun getDoNotShowMessage() = DvcsBundle.message("action.force.push.don.t.warn.about.this.target")
}
