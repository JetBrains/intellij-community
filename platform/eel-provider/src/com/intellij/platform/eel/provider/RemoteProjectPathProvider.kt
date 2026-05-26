// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("RemoteProjectPathProviderKt")
package com.intellij.platform.eel.provider

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private val LOG = fileLogger()

private val REMOTE_PROJECT_BASE_PATH_KEY: Key<String> = Key.create("com.intellij.platform.remoteProjectBasePath")

/**
 * Empty string is the "checked-absent" sentinel; the absence of any call is "not yet set"
 * and treated as an init-order error on a thin client.
 */
@ApiStatus.Internal
fun Project.setRemoteProjectBaseNioPath(rawHostPath: String) {
  putUserData(REMOTE_PROJECT_BASE_PATH_KEY, rawHostPath)
}

/**
 * Returns the project's base directory on the **host** (backend) machine as a [Path]
 * routed through `MultiRoutingFileSystem`.
 *
 * On a thin client [Project.guessProjectDir] resolves against the client's *fake* project,
 * not the host root, so callers that need the host path can't use it directly — that's the
 * whole reason for this provider. On a monolith it falls back to [Project.guessProjectDir].
 */
@ApiStatus.Internal
fun Project.getRemoteProjectBaseNioPath(): Path? {
  // The backend-provided path is a string in the host's path syntax, so it must be parsed
  // through EelPath to be routed via MultiRoutingFileSystem to the correct environment.
  // The local fallback uses VirtualFile.toNioPathOrNull, which goes through the VFS to get
  // the correct local NIO path (including FS-specific quirks like the Windows bare-drive fix).
  val basePathFromBackend = getUserData(REMOTE_PROJECT_BASE_PATH_KEY)

  if (basePathFromBackend == null) {
    // Only treat the missing key as an init-order regression when a remote EelDescriptor is
    // actually attached: ThinClientRdProjectViewSession.beforeInit always sets both. If the
    // descriptor is still LocalEelDescriptor, there's no real RD session yet (traverseUI /
    // searchable-options indexing run in the JetBrainsClient JVM without opening a Solution),
    // so falling through to the local heuristic is correct.
    if (PlatformUtils.isJetBrainsClient() && getEelDescriptor() !is LocalEelDescriptor) {
      LOG.error("REMOTE_PROJECT_BASE_PATH_KEY not set on thin client; init order is broken")
      return null
    }
  }
  else {
    if (basePathFromBackend.isEmpty()) {
      // Checked-absent sentinel.
      return null
    }

    val descriptor = getEelDescriptor()

    return try {
      EelPath.parse(basePathFromBackend, descriptor).asNioPath()
    }
    catch (e: EelPathException) {
      LOG.error("Path '$basePathFromBackend' inconsistent with descriptor $descriptor", e)
      null
    }
  }

  return guessProjectDir()?.toNioPathOrNull()
}