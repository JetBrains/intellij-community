// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SdkUtils")
@file:ApiStatus.Internal

package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path

fun findClashingSdk(sdkName: String, sdk: Sdk): SdkEntity? {
  val machine = sdk.homePath?.let { Path.of(it) }?.getEelDescriptor()?.machine ?: LocalEelMachine
  val relevantSnapshot = GlobalWorkspaceModel.getInstance(machine).currentSnapshot
  return relevantSnapshot.entities(SdkEntity::class.java).find { it.name == sdkName }
}

fun getEelDescriptorOfHomePath(homePath: String): EelDescriptor =
  try {
    Path.of(homePath).getEelDescriptor()
  }
  catch (_: InvalidPathException) {
    LocalEelDescriptor
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
  name: String,
  sdkType: SdkTypeId,
  homePathForEnvironmentDetection: String,
): Sdk =
  if (this is EnvironmentScopedSdkTableOps) {
    val eelDescriptor = getEelDescriptorOfHomePath(homePathForEnvironmentDetection)
    createSdk(name, sdkType, eelDescriptor)
  }
  else {
    createSdk(name, sdkType)
  }