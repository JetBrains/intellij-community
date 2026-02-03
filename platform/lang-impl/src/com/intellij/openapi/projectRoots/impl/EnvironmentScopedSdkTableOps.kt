// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import org.jetbrains.annotations.ApiStatus

/**
 * Environment‑scoped SDK operations built on top of [com.intellij.openapi.projectRoots.ProjectJdkTable].
 *
 * Implementations expose lookup and creation APIs that are anchored to a specific eel environment (e.g., local,
 * WSL distribution, or a Docker container) so the underlying
 * [com.intellij.platform.workspace.jps.entities.SdkEntity]s are stored in the correct environment‑specific
 * [com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel].
 *
 * This is an internal SPI implemented by the platform's [com.intellij.openapi.projectRoots.ProjectJdkTable] provider.
 *
 * @see com.intellij.openapi.projectRoots.ProjectJdkTable
 * @see com.intellij.platform.eel.EelDescriptor
 */
@ApiStatus.Internal
interface EnvironmentScopedSdkTableOps {
  fun findJdk(name: String, eelMachine: EelMachine): Sdk?

  fun findJdk(name: String, type: String, eelMachine: EelMachine): Sdk?

  /**
   * Creates an SDK whose backing entity is stored in the [com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel]
   * associated with the provided [eelDescriptor].
   *
   * This method augments [com.intellij.openapi.projectRoots.impl.ProjectJdkTableImpl.createSdk], which always creates
   * SDKs in the local environment. By supplying an [EelDescriptor], callers ensure that the SDK is created in the
   * correct isolated environment (e.g., a particular WSL distribution or Docker container), so the following
   * resolution and persistence operate in the appropriate environment namespace.
   *
   * @param name human‑readable SDK name.
   * @param sdkType the SDK type.
   * @param eelDescriptor target environment descriptor that defines which environment's
   *                      [com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel] will own the created SDK.
   * @return a new [Sdk] registered in the environment specified by [eelDescriptor].
   */
  fun createSdk(name: String, sdkType: SdkTypeId, eelMachine: EelMachine): Sdk
}