// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TrustedProjects")

package com.intellij.ide.impl

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SystemProperties
import com.intellij.util.ThreeState
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.Nls
import java.nio.file.Paths

fun confirmOpeningUntrustedProject(virtualFile: VirtualFile, name: @Nls String): OpenUntrustedProjectChoice {
  if (isTrustedCheckDisabled()) {
    return OpenUntrustedProjectChoice.IMPORT
  }
  if (service<TrustedPathsSettings>().isPathTrusted(virtualFile.toNioPath())) {
    return OpenUntrustedProjectChoice.IMPORT
  }
  val projectDir = if (virtualFile.isDirectory) virtualFile else virtualFile.parent
  val origin = getProjectOrigin(projectDir.toNioPath())
  if (origin != null && service<TrustedHostsSettings>().isHostTrusted(origin)) {
    return OpenUntrustedProjectChoice.IMPORT
  }

  val dontAskAgain = if (origin != null) {
    object : DialogWrapper.DoNotAskOption.Adapter() {
      override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
        if (isSelected && exitCode == Messages.YES) {
          service<TrustedHostsSettings>().setHostTrusted(origin, true)
        }
      }

      override fun getDoNotShowMessage(): String {
        return IdeBundle.message("untrusted.project.warning.trust.host.checkbox", origin)
      }
    }
  }
  else null

  val choice = MessageDialogBuilder.yesNoCancel(title = IdeBundle.message("untrusted.project.warning.title", name),
                                                message = IdeBundle.message("untrusted.project.open.warning.text", name))
    .yesText(IdeBundle.message("untrusted.project.open.warning.button.import"))
    .noText(IdeBundle.message("untrusted.project.open.warning.button.open.without.import"))
    .cancelText(CommonBundle.getCancelButtonText())
    .doNotAsk(dontAskAgain)
    .asWarning()
    .show(project = null)

  return when (choice) {
    Messages.YES -> OpenUntrustedProjectChoice.IMPORT
    Messages.NO -> OpenUntrustedProjectChoice.OPEN_WITHOUT_IMPORTING
    Messages.CANCEL -> OpenUntrustedProjectChoice.CANCEL
    else -> {
      LOG.error("Illegal choice $choice")
      return OpenUntrustedProjectChoice.CANCEL
    }
  }
}

fun confirmImportingUntrustedProject(project: Project, @Nls buildSystemName: String, @Nls importButtonText: String): Boolean {
  if (isTrustedCheckDisabled()) {
    return true
  }
  val projectDir = project.basePath?.let { Paths.get(it) }
  if (projectDir != null && service<TrustedPathsSettings>().isPathTrusted(projectDir)) {
    return true
  }
  val origin = getProjectOrigin(projectDir)
  if (origin != null && service<TrustedHostsSettings>().isHostTrusted(origin)) {
    return true
  }

  val answer = MessageDialogBuilder.yesNo(title = IdeBundle.message("untrusted.project.warning.title", buildSystemName),
                                          message = IdeBundle.message("untrusted.project.import.warning.text", buildSystemName))
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
}

fun Project.isTrusted() = this.service<TrustedProjectSettings>().trustedState == ThreeState.YES

fun Project.getTrustedState() = this.service<TrustedProjectSettings>().trustedState

fun Project.setTrusted(value: Boolean) {
  this.service<TrustedProjectSettings>().trustedState = ThreeState.fromBoolean(value)
}

internal fun isTrustedCheckDisabled() = ApplicationManager.getApplication().isUnitTestMode ||
                                        ApplicationManager.getApplication().isHeadlessEnvironment ||
                                        SystemProperties.`is`("idea.is.integration.test")

@State(name = "Trusted.Project.Settings", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class TrustedProjectSettings : SimplePersistentStateComponent<TrustedProjectSettings.State>(State()) {

  class State : BaseState() {
    @get:Attribute
    var isTrusted by enum(ThreeState.UNSURE)
  }

  var trustedState: ThreeState
    get() = if (isTrustedCheckDisabled()) ThreeState.YES else state.isTrusted
    set(value) {
      state.isTrusted = value
    }
}

private val LOG = Logger.getInstance("com.intellij.ide.impl.TrustedProjects")

