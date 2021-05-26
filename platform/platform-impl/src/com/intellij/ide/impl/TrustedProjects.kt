// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TrustedProjects")

package com.intellij.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.TrustedCheckResult.NotTrusted
import com.intellij.ide.impl.TrustedCheckResult.Trusted
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
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.Attribute
import java.nio.file.Path
import java.nio.file.Paths

fun confirmOpeningUntrustedProject(
  virtualFile: VirtualFile,
  createDialog: () -> MessageDialogBuilder.YesNoCancel
): OpenUntrustedProjectChoice {
  val projectDir = if (virtualFile.isDirectory) virtualFile else virtualFile.parent
  val trustedCheckResult = isProjectImplicitlyTrusted(projectDir.toNioPath())
  if (trustedCheckResult is Trusted) {
    return OpenUntrustedProjectChoice.IMPORT
  }

  val choice = createDialog()
    .doNotAsk(createDoNotAskOptionForHost(trustedCheckResult))
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

fun confirmLoadingUntrustedProject(project: Project, createDialog: () -> MessageDialogBuilder.YesNo): Boolean {
  val trustedCheckResult = isProjectImplicitlyTrusted(project)
  if (trustedCheckResult is Trusted) {
    project.setTrusted(true)
    return true
  }

  val answer = createDialog()
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

fun Project.getExplicitTrustedStateOrByHostAndLocation(): ThreeState {
  val explicit = getTrustedState()
  if (explicit != ThreeState.UNSURE) return explicit

  return if (isProjectImplicitlyTrusted(this) is Trusted) {
    ThreeState.YES
  }
  else {
    ThreeState.UNSURE
  }
}

fun Project.setTrusted(value: Boolean) {
  this.service<TrustedProjectSettings>().trustedState = ThreeState.fromBoolean(value)
  if(value) {
    ApplicationManager.getApplication().messageBus.syncPublisher(TrustChangeNotifier.TOPIC).projectTrusted(this)
  }
}

fun createDoNotAskOptionForHost(project: Project): DialogWrapper.DoNotAskOption? {
  return createDoNotAskOptionForHost(isProjectImplicitlyTrusted(project))
}

private fun createDoNotAskOptionForHost(trustedCheckResult: TrustedCheckResult): DialogWrapper.DoNotAskOption? {
  if (trustedCheckResult !is NotTrusted) return null

  val url = trustedCheckResult.url
  val origin = if (url == null) null else getOriginFromUrl(url)?.host
  return if (origin != null) {
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
}

private fun isTrustedCheckDisabled() = ApplicationManager.getApplication().isUnitTestMode ||
                                       ApplicationManager.getApplication().isHeadlessEnvironment ||
                                       SystemProperties.`is`("idea.is.integration.test")

private sealed class TrustedCheckResult {
  object Trusted: TrustedCheckResult()
  class NotTrusted(val url: String?): TrustedCheckResult()
}

private fun isProjectImplicitlyTrusted(project: Project): TrustedCheckResult = isProjectImplicitlyTrusted(project.basePath?.let { Paths.get(it) })

private fun isProjectImplicitlyTrusted(projectDir: Path?): TrustedCheckResult {
  if (isTrustedCheckDisabled()) {
    return Trusted
  }
  if (projectDir != null && service<TrustedPathsSettings>().isPathTrusted(projectDir)) {
    return Trusted
  }
  val url = getProjectOriginUrl(projectDir)
  if (url != null && service<TrustedHostsSettings>().isUrlTrusted(url)) {
    return Trusted
  }
  return NotTrusted(url)
}

@State(name = "Trusted.Project.Settings", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class TrustedProjectSettings : SimplePersistentStateComponent<TrustedProjectSettings.State>(State()) {

  class State : BaseState() {
    @get:Attribute
    var isTrusted by enum(ThreeState.UNSURE)

    @get:Attribute
    var hasCheckedIfOldProject by property(false)
  }

  var trustedState: ThreeState
    get() = if (isTrustedCheckDisabled()) ThreeState.YES else state.isTrusted
    set(value) {
      state.isTrusted = value
    }

  var hasCheckedIfOldProject: Boolean
    get() = state.hasCheckedIfOldProject
    set(value) {
      state.hasCheckedIfOldProject = value
    }
}

interface TrustChangeNotifier {
  fun projectTrusted(project: Project)
  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC = Topic.create("Trusted project status", TrustChangeNotifier::class.java)
  }

}

private val LOG = Logger.getInstance("com.intellij.ide.impl.TrustedProjects")

