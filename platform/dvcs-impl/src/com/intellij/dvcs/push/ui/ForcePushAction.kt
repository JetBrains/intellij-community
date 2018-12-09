// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push.ui

import com.intellij.dvcs.push.PushSupport
import com.intellij.dvcs.push.PushTarget
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showOkCancelDialog
import com.intellij.xml.util.XmlStringUtil

class ForcePushAction : PushActionBase() {

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
    return if (!enabled && prohibitedTarget != null) {
      "Force push to ${prohibitedTarget.presentation} is prohibited"
    }
    else null
  }

  private fun confirmForcePush(project: Project, ui: VcsPushUi): Boolean {
    val silentForcePushIsNotAllowed = ui.selectedPushSpecs.mapValues { (support, pushInfos) ->
      pushInfos.filter { it -> !support.isSilentForcePushAllowed(it.pushSpec.target) }
    }.filterValues { it.isNotEmpty() }

    if (silentForcePushIsNotAllowed.isEmpty()) return true

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

    val to = if (commonTarget != null) " to <b>${commonTarget.presentation}</b>" else ""
    val message = "You're going to force push${to}. It may overwrite commits at the remote. Are you sure you want to proceed?"
    val myDoNotAskOption = if (commonTarget != null) MyDoNotAskOptionForPush(aSupport!!, commonTarget) else null
    val decision = showOkCancelDialog(title = "Force Push", message = XmlStringUtil.wrapInHtml(message),
                                      okText = "&Force Push",
                                      icon = Messages.getWarningIcon(), doNotAskOption = myDoNotAskOption,
                                      project = project)
    return decision == Messages.OK
  }
}

private class MyDoNotAskOptionForPush(private val pushSupport: PushSupport<*, *, PushTarget>,
                                      private val commonTarget: PushTarget) : DialogWrapper.DoNotAskOption.Adapter() {
  override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
    if (exitCode == Messages.OK && isSelected) {
      pushSupport.saveSilentForcePushTarget(commonTarget)
    }
  }

  override fun getDoNotShowMessage() = "Don't warn about this target"
}
