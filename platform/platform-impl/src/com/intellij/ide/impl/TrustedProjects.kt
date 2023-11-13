// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TrustedProjects")
@file:ApiStatus.Experimental

package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsDialog
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ThreeState
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
@ApiStatus.ScheduledForRemoval
@Deprecated("Use com.intellij.ide.impl.trustedProjects.TrustedProjectsDialog instead")
fun confirmOpeningOrLinkingUntrustedProject(
  projectRoot: Path,
  project: Project,
  @NlsContexts.DialogTitle title: String,
  @NlsContexts.DialogMessage message: String,
  @NlsContexts.Button trustButtonText: String,
  @NlsContexts.Button distrustButtonText: String,
  @NlsContexts.Button cancelButtonText: String
): Boolean = TrustedProjectsDialog.confirmOpeningOrLinkingUntrustedProject(
  projectRoot, project, title, message, trustButtonText, distrustButtonText, cancelButtonText
)

@Suppress("DEPRECATION", "DeprecatedCallableAddReplaceWith")
@Deprecated("Use com.intellij.ide.impl.trustedProjects.TrustedProjectsDialog instead")
fun confirmLoadingUntrustedProject(
  project: Project,
  @NlsContexts.DialogTitle title: String,
  @NlsContexts.DialogMessage message: String,
  @NlsContexts.Button trustButtonText: String,
  @NlsContexts.Button distrustButtonText: String
): Boolean = TrustedProjectsDialog.confirmLoadingUntrustedProject(
  project, title, message, trustButtonText, distrustButtonText
)

@ApiStatus.Internal
enum class OpenUntrustedProjectChoice {
  TRUST_AND_OPEN,
  OPEN_IN_SAFE_MODE,
  CANCEL;
}

fun Project.isTrusted(): Boolean {
  return TrustedProjects.isProjectTrusted(TrustedProjectsLocator.locateProject(this))
}

fun Project.setTrusted(isTrusted: Boolean) {
  TrustedProjects.setProjectTrusted(TrustedProjectsLocator.locateProject(this), isTrusted)
}

@Suppress("unused") // Used externally
fun Project.getTrustedState(): ThreeState {
  return TrustedProjects.getProjectTrustedState(TrustedProjectsLocator.locateProject(this))
}

@Suppress("unused") // Used externally
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

const val TRUSTED_PROJECTS_HELP_TOPIC: String = "Project_security"
