// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TrustedProjects")

package com.intellij.ide.impl

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.util.ThreeState
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.Nls

fun confirmOpeningUntrustedProject(name: @Nls String): OpenUntrustedProjectChoice {
  val choice = MessageDialogBuilder.yesNoCancel(title = IdeBundle.message("untrusted.project.open.warning.title", name),
                                                message = IdeBundle.message("untrusted.project.open.warning.text", name))
    .yesText(IdeBundle.message("untrusted.project.open.warning.button.import"))
    .noText(IdeBundle.message("untrusted.project.open.warning.button.open.without.import"))
    .cancelText(CommonBundle.getCancelButtonText())
    .asWarning()
    .show(project = null)

  return when (choice) {
    Messages.YES -> OpenUntrustedProjectChoice.IMPORT
    Messages.NO -> OpenUntrustedProjectChoice.OPEN_WITHOUT_IMPORTING
    Messages.CANCEL -> OpenUntrustedProjectChoice.CANCEL
    else -> {
      OpenUntrustedProjectChoice.log.error("Illegal choice $choice")
      return OpenUntrustedProjectChoice.CANCEL
    }
  }
}

fun confirmImportingUntrustedProject(project: Project, @Nls buildSystemName: String, @Nls importButtonText: String): Boolean {
  val answer = MessageDialogBuilder.yesNo(title = IdeBundle.message("untrusted.project.open.warning.title", buildSystemName),
                                          message = IdeBundle.message("untrusted.project.open.warning.text", buildSystemName))
    .yesText(importButtonText)
    .noText(CommonBundle.getCancelButtonText())
    .asWarning()
    .ask(project)

  project.setTrusted(answer)
  return answer
}

enum class OpenUntrustedProjectChoice {
  IMPORT,
  OPEN_WITHOUT_IMPORTING,
  CANCEL;

  companion object {
    val log = logger<OpenUntrustedProjectChoice>()
  }
}

fun Project.isTrusted() = this.service<TrustedProjectSettings>().trustedState == ThreeState.YES

fun Project.getTrustedState() = this.service<TrustedProjectSettings>().trustedState

fun Project.setTrusted(value: Boolean) {
  this.service<TrustedProjectSettings>().trustedState = ThreeState.fromBoolean(value)
}

@State(name = "Trusted.Project.Settings", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class TrustedProjectSettings : SimplePersistentStateComponent<TrustedProjectSettings.State>(State()) {

  class State : BaseState() {
    @get:Attribute
    var isTrusted by enum(ThreeState.UNSURE)
  }

  var trustedState: ThreeState
    get() = if (isTestMode()) ThreeState.YES else state.isTrusted
    set(value) {
      state.isTrusted = value
    }

  private fun isTestMode() = ApplicationManager.getApplication().isUnitTestMode
}
