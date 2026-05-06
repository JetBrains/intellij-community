// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("RemoteProjectPathProviderKt")
package com.intellij.platform.eel.provider

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCustomDataSynchronizer
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Key
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.reflect.KType
import kotlin.reflect.typeOf

private val REMOTE_PROJECT_BASE_PATH_KEY: Key<String> = Key.create("com.intellij.platform.remoteProjectBasePath")

/**
 * Returns the project's base directory on the **host** (backend) machine as a [Path]
 * routed through `MultiRoutingFileSystem` (so it carries the right
 * [com.intellij.platform.eel.EelDescriptor]).
 *
 * Why this exists:
 * - In a monolith IDE, [Project.guessProjectDir] is the project root and is enough.
 * - In Remote Development on the thin client, [Project.guessProjectDir] resolves against
 *   the *client's* fake project, NOT the project root on the host. Code that needs the
 *   host path (UI presentation, relative-path computation, building a [Path] through
 *   `MultiRoutingFileSystem`) cannot rely on [Project.guessProjectDir] there.
 *
 * On a thin client the host path is delivered by the synchronizer in this file; on a
 * monolith it falls back to [Project.guessProjectDir].
 *
 * The returned [Path] is dispatched by `MultiRoutingFileSystem` to the appropriate
 * NIO `FileSystemProvider` for the target environment (default FS, WSL, Docker, SSH, …),
 * so `java.nio.file.Files.*` calls work for environments that have a registered provider.
 */
@ApiStatus.Internal
fun Project.getRemoteProjectBaseNioPath(): Path? {
  val raw = getUserData(REMOTE_PROJECT_BASE_PATH_KEY) ?: guessProjectDir()?.path ?: return null
  return EelPath.parse(raw, getEelDescriptor()).asNioPath()
}

/**
 * Streams the host project base directory to the thin client so that
 * [getRemoteProjectBaseNioPath] returns the host path on the frontend.
 *
 * Mirrors `com.intellij.platform.vcs.impl.shared.ProjectBasePathSynchronizer` (which is
 * `internal` to VCS). Kept platform-level so non-VCS callers can use it without depending
 * on `intellij.platform.vcs.impl.shared`. See TODO(unify-with-vcs) below.
 */
internal class RemoteProjectBasePathSynchronizer : ProjectCustomDataSynchronizer<String> {
  override val id: String = "platform.remoteProjectBasePath"
  override val dataType: KType = typeOf<String>()

  override fun getValues(project: Project): Flow<String> {
    // Backend-side: guessProjectDir resolves to the actual host project root.
    // (On the thin client it would resolve against client-local fake state, but
    // [getValues] is only ever invoked on the backend per the EP contract.)
    val dir = project.guessProjectDir() ?: return emptyFlow()
    return flowOf(dir.path)
  }

  override suspend fun consumeValue(project: Project, value: String) {
    project.putUserData(REMOTE_PROJECT_BASE_PATH_KEY, value)
  }
}

// TODO(unify-with-vcs): Today there are two places that hold the host project base path
//   on the thin client: this provider and `ProjectBasePathHolder` in
//   `intellij.platform.vcs.impl.shared`. The VCS one was the original; this one was added
//   for non-VCS callers. Pick a single owner: either drop `ProjectBasePathHolder` and
//   make VCS read from here, or move the holder into platform and have this file delegate
//   to it. Coordinate with VCS owners before unifying.
