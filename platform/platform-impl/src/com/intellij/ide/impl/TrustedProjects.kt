// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TrustedProjects")
@file:ApiStatus.Experimental

package com.intellij.ide.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ThreeState
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
  return TrustedProjectsDialog.confirmLoadingUntrustedProject(project, title, message, trustButtonText, distrustButtonText)
}

@ApiStatus.Internal // Used in MPS
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

@ApiStatus.Internal // Used in MPS
const val TRUSTED_PROJECTS_HELP_TOPIC: String = "Project_security"
