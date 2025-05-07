// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TrustedProjects")
@file:ApiStatus.Experimental

package com.intellij.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsDialog
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ThreeState
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
@Deprecated("Use com.intellij.ide.impl.trustedProjects.TrustedProjectsDialog instead")
fun confirmLoadingUntrustedProject(
  project: Project,
  @NlsContexts.DialogTitle title: String,
  @NlsContexts.DialogMessage message: String,
  @NlsContexts.Button trustButtonText: String,
  @NlsContexts.Button distrustButtonText: String
): Boolean {
  return TrustedProjectsDialog.confirmLoadingUntrustedProject(
    project = project,
    title = title,
    message = message,
    trustButtonText = trustButtonText,
    distrustButtonText = distrustButtonText
  )
}

@ApiStatus.Internal
enum class OpenUntrustedProjectChoice {
  TRUST_AND_OPEN,
  OPEN_IN_SAFE_MODE,
  CANCEL;
}

@Suppress("unused") // Used externally
@Deprecated(
  "Use TrustedProjects.isProjectTrusted instead",
  ReplaceWith(
    "TrustedProjects.isProjectTrusted(this)",
    "com.intellij.ide.trustedProjects.TrustedProjects"
  )
)
fun Project.isTrusted(): Boolean {
  return TrustedProjects.isProjectTrusted(this)
}

@Suppress("unused") // Used externally
@Deprecated(
  "Use TrustedProjects.setProjectTrusted instead",
  ReplaceWith(
    "TrustedProjects.setProjectTrusted(this, isTrusted)",
    "com.intellij.ide.trustedProjects.TrustedProjects"
  )
)
fun Project.setTrusted(isTrusted: Boolean) {
  TrustedProjects.setProjectTrusted(this, isTrusted)
}

@Suppress("unused") // Used externally
@Deprecated("Use TrustedProjects.isProjectTrusted instead")
fun Project.getTrustedState(): ThreeState {
  return TrustedProjects.getProjectTrustedState(this)
}

@Suppress("unused") // Used externally
@Deprecated("Use TrustedProjects.isTrustedCheckDisabled instead")
fun isTrustedCheckDisabled(): Boolean {
  return TrustedProjects.isTrustedCheckDisabled()
}

@Suppress("DEPRECATION")
@Deprecated("Use TrustedProjectsListener instead")
interface TrustStateListener {

  fun onProjectTrusted(project: Project) {
  }

  fun onProjectUntrusted(project: Project) {
  }

  fun onProjectTrustedFromNotification(project: Project) {
  }

  class Bridge : TrustedProjectsListener {

    override fun onProjectTrusted(project: Project) {
      ApplicationManager.getApplication().messageBus
        .syncPublisher(TOPIC)
        .onProjectTrusted(project)
    }

    override fun onProjectUntrusted(project: Project) {
      ApplicationManager.getApplication().messageBus
        .syncPublisher(TOPIC)
        .onProjectUntrusted(project)
    }

    override fun onProjectTrustedFromNotification(project: Project) {
      ApplicationManager.getApplication().messageBus
        .syncPublisher(TOPIC)
        .onProjectTrustedFromNotification(project)
    }
  }

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<TrustStateListener> = Topic(TrustStateListener::class.java, Topic.BroadcastDirection.NONE)
  }
}

/**
 * Used in MPS
 */
@ApiStatus.Internal
const val TRUSTED_PROJECTS_HELP_TOPIC: String = "Project_security"

private class ShowTrustProjectDialogAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && !project.isDefault && !project.isTrusted()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    if (TrustedProjectsDialog.confirmLoadingUntrustedProject(
        project = project,
        title = IdeBundle.message("untrusted.project.general.dialog.title"),
        message = IdeBundle.message("untrusted.project.open.dialog.text", ApplicationInfoEx.getInstanceEx().fullApplicationName),
        trustButtonText = IdeBundle.message("untrusted.project.dialog.trust.button"),
        distrustButtonText = IdeBundle.message("untrusted.project.dialog.distrust.button")
      )
    ) {
      ApplicationManager.getApplication().messageBus
        .syncPublisher(TrustedProjectsListener.TOPIC)
        .onProjectTrusted(project)
    }
  }
}
