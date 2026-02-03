// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SdkUtils")
@file:ApiStatus.Internal

package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.*
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path

fun findClashingSdk(project: Project?, sdkName: String, sdk: Sdk): SdkEntity? {
  val machine = project?.getEelMachine() ?: sdk.homePath?.let { Path.of(it) }?.getEelDescriptor()?.getResolvedEelMachine() ?: LocalEelMachine
  val relevantSnapshot = GlobalWorkspaceModel.getInstance(machine).currentSnapshot
  return relevantSnapshot.entities(SdkEntity::class.java).find { it.name == sdkName }
}

/**
 * Returns the [EelDescriptor] that the Workspace Model should use for SDK entities
 * associated with the given `homePath`.
 *
 * This helper abstracts the current "per-environment" separation of the global
 * workspace model by EEL descriptors:
 *
 * - If the registry key `ide.workspace.model.per.environment.model.separation` is ON,
 *   the descriptor is inferred from `homePath` via `Path.getEelDescriptor()` and thus
 *   points to the machine that actually owns the path (e.g., local OS, a specific WSL
 *   distribution, a remote/container environment).
 * - If the registry key is OFF, the local machine descriptor ([LocalEelDescriptor]) is
 *   always returned so that SDKs from different environments are kept together in a
 *   single, shared model. This escape hatch is required by IDEs that expect certain
 *   remote interpreters (e.g., WSL) to be visible from both local and remote projects,
 *   where strict separation would otherwise break discovery or reuse.
 * - If `homePath` cannot be parsed into a valid [Path], the method falls back to
 *   [LocalEelDescriptor].
 *
 * The returned descriptor determines which instance of [GlobalWorkspaceModel] will hold
 * the corresponding [SdkEntity] records, preventing cross-environment linkage issues
 * when separation is enabled, while preserving legacy behavior when it is disabled.
 *
 * @param homePath a string path used solely to infer the owning environment; it does not
 *                 need to exist locally and is not validated beyond basic path parsing
 * @return the effective environment ([EelDescriptor]) to use for workspace model operations
 * @see com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
 * @see com.intellij.platform.eel.provider.getEelDescriptor
 */
fun getEffectiveWorkspaceEelDescriptorOfHomePath(homePath: String): EelDescriptor {
  if (!Registry.`is`("ide.workspace.model.per.environment.model.separation", false)) {
    return LocalEelDescriptor
  }
  return try {
    Path.of(homePath).getEelDescriptor()
  }
  catch (_: InvalidPathException) {
    LocalEelDescriptor
  }
}

/**
 * Creates an SDK in the [GlobalWorkspaceModel] that matches the eek environment inferred
 * from the given path, instead of always using the local environment.
 *
 * The [homePathForEnvironmentDetection] is not set as the SDK home; it is used solely to
 * determine the target environment (e.g., WSL distribution, Docker container, or local OS)
 * via `Path.getEelDescriptor()`. This ensures the underlying [SdkEntity] is created in the
 * correct environment-specific storage, avoiding cross-environment linkage issues.
 *
 * Prefer this over `ProjectJdkTable.getInstance().createSdk(String, SdkTypeId)` when the SDK's
 * real home may reside in a non-local environment, or when the environment cannot be assumed
 * to be local.
 *
 * Notes:
 * - Callers must set the SDK home separately via `SdkModificator.setHomePath(...)` as needed.
 * - If the provided path is invalid, the local environment is assumed.
 *
 * @param name human-readable SDK name
 * @param sdkType SDK type
 * @param homePathForEnvironmentDetection a path that exists in the intended environment and
 *                                        can be used to infer the [EelDescriptor];
 *                                        it is not assigned as the SDK home
 * @return a newly created [Sdk] associated with the specific environment
 */
fun ProjectJdkTable.createSdkForEnvironment(
  project: Project?,
  name: String,
  sdkType: SdkTypeId,
  homePathForEnvironmentDetection: String,
): Sdk =
  if (this is EnvironmentScopedSdkTableOps) {
    val eelDescriptor = getEffectiveWorkspaceEelDescriptorOfHomePath(homePathForEnvironmentDetection)
    createSdk(name, sdkType, project?.getEelMachine() ?: eelDescriptor.getResolvedEelMachine() ?: LocalEelMachine)
  }
  else {
    createSdk(name, sdkType)
  }