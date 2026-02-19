// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.workspaceModel.ide.toPath
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Determines whether a resource with the given home path is located on this [EelMachine].
 *
 * Location is determined by the per-environment model separation flag
 * (`ide.workspace.model.per.environment.model.separation`):
 *
 * - When separation is **enabled**, checks via [EelMachine.ownsPath] to determine
 *   if the path belongs to this machine (e.g., local OS, WSL distribution, 
 *   Docker container, SSH host).
 * - When separation is **disabled**, returns `true` for all resources regardless of path,
 *   maintaining legacy behavior where all environments share a single global model.
 * - If [extractHomePath] returns `null` or throws [InvalidPathException],
 *   falls back to checking if this machine is [LocalEelMachine].
 *
 * @param resource The resource to check
 * @param extractHomePath Lambda that extracts the home path from the resource
 * @return `true` if the resource is located on this machine
 */
private fun <T> EelMachine.ownsHomePath(resource: T, extractHomePath: (T) -> Path?): Boolean {
  if (!Registry.`is`("ide.workspace.model.per.environment.model.separation", false)) {
    return true
  }

  return try {
    val nioPath = extractHomePath(resource)

    return if (nioPath != null) {
      ownsPath(nioPath)
    }
    else {
      this == LocalEelMachine
    }
  }
  catch (_: InvalidPathException) {
    this == LocalEelMachine
  }
}

/**
 * Checks whether the given [SdkEntity] is located on this [EelMachine].
 *
 * @see EelMachine.ownsHomePath
 */
internal fun EelMachine.ownsSdkEntry(sdkEntity: SdkEntity): Boolean {
  return ownsHomePath(sdkEntity) { it.homePath?.toPath() }
}

/**
 * Checks whether the given [Sdk] is located on this [EelMachine].
 *
 * @see EelMachine.ownsHomePath
 */
internal fun EelMachine.ownsSdk(sdk: Sdk): Boolean {
  return ownsHomePath(sdk) { it.homePath?.let(::Path) }
}