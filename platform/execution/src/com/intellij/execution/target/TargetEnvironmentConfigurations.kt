// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TargetEnvironmentConfigurations")

package com.intellij.execution.target

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.local.LocalTargetType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe

private val Project.defaultTargetName: @NlsSafe String?
  get() = TargetEnvironmentsManager.getInstance(this).defaultTarget?.displayName

@JvmOverloads
fun RunProfile.getEffectiveTargetName(project: Project? = null): @NlsSafe String? =
  (this as? TargetEnvironmentAwareRunProfile)?.getEffectiveTargetName(project)

/**
 * @see TargetEnvironmentsManager.defaultTarget
 */
@JvmOverloads
fun TargetEnvironmentAwareRunProfile.getEffectiveTargetName(project: Project? = null): @NlsSafe String? {
  return when (defaultTargetName) {
    LocalTargetType.LOCAL_TARGET_NAME -> null
    else -> defaultTargetName ?: project?.defaultTargetName
  }
}

/**
 * @see TargetEnvironmentsManager.defaultTarget
 */
fun getEffectiveConfiguration(runProfile: RunProfile, project: Project): TargetEnvironmentConfiguration? {
  if (runProfile !is TargetEnvironmentAwareRunProfile) return null

  val targetName = runProfile.defaultTargetName

  return getEffectiveConfiguration(targetName, project)
}

/**
 * @see TargetEnvironmentsManager.defaultTarget
 */
fun getEffectiveConfiguration(targetName: String?, project: Project): TargetEnvironmentConfiguration? =
  when {
    targetName == LocalTargetType.LOCAL_TARGET_NAME -> null
    targetName != null -> TargetEnvironmentsManager.getInstance(project).targets.findByName(targetName)
    else -> TargetEnvironmentsManager.getInstance(project).defaultTarget
  }

/**
 * Returns the display name of the effective target configuration.
 *
 * Differs from [getEffectiveConfiguration] that it does not search for the target configuration but rather uses the provided [targetName].
 */
fun getEffectiveTargetName(targetName: String?, project: Project): @NlsSafe String? =
  when {
    targetName == LocalTargetType.LOCAL_TARGET_NAME -> null
    targetName != null -> targetName
    else -> TargetEnvironmentsManager.getInstance(project).defaultTarget?.displayName
  }

fun createEnvironmentRequest(runProfile: RunProfile, project: Project): TargetEnvironmentRequest =
  getEffectiveConfiguration(runProfile, project)?.createEnvironmentRequest(project) ?: LocalTargetEnvironmentRequest()