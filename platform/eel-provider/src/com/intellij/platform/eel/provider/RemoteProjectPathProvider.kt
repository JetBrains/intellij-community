// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("RemoteProjectPathProviderKt")
package com.intellij.platform.eel.provider

import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.util.annotations.VisibleToClasses
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private val LOG = fileLogger()

private val REMOTE_PROJECT_BASE_PATH_KEY: Key<String> = Key.create("com.intellij.platform.remoteProjectBasePath")
private val REMOTE_PROJECT_IDENTITY_PATH_KEY: Key<String> = Key.create("com.intellij.platform.remoteProjectIdentityPath")

/**
 * Empty string is the "checked-absent" sentinel; the absence of any call is "not yet set"
 * and treated as an init-order error on a thin client.
 */
@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.air.shared.core.AirAgentEnvironmentTest",
  "com.intellij.air.shared.core.AirProjectPathsTest",
  "com.intellij.ide.lightProducts.LightProjectOpeningKt",
  "com.jetbrains.thinclient.connection.ThinClientRdProjectViewSession",
)
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
@ApiStatus.Experimental
@Deprecated("Temporary API for integrating remote development with Eel. Use with caution; see the function documentation.")
@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.air.acp.frontend.AgentTerminalAuthenticationFrontendService",
  "com.intellij.air.frontend.core.ProjectBackendBasePathKt",
  "com.intellij.air.frontend.core.ProjectSessionWorkspaceIdKt",
  "com.intellij.air.frontend.session.toolwindow.actions.AgentProjectChooserServiceImpl",
  "com.intellij.air.frontend.worktree.settings.AirWorktreeParentLocationServiceImpl",
  "com.intellij.air.light.extensions.LightExtensionCatalog",
  "com.intellij.clion.stm32.wrapper.frontend.CubeTerminalOpenerKt",
  "com.intellij.code.findings.api.FindingsProjectRoot",
  "com.intellij.code.findings.simplified.prompt.FindingsPromptSupport",
  "com.intellij.lsp.ui.frontend.ConfigurableLspClientDescriptor",
  "com.intellij.lsp.ui.frontend.settings.LspServerConfigurable",
  "org.jetbrains.plugins.terminal.LocalTerminalDirectRunner",
  "org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider",
  "org.jetbrains.plugins.terminal.runner.LocalOptionsConfigurer",
)
fun Project.getRemoteProjectBaseNioPath(): Path? {
  // The local fallback uses VirtualFile.toNioPathOrNull, which goes through the VFS to get
  // the correct local NIO path (including FS-specific quirks like the Windows bare-drive fix).
  return getRemoteProjectBaseNioPath(whenNothingStored = { guessProjectDir()?.toNioPathOrNull() })
}

/**
 * Same as [getRemoteProjectBaseNioPath], but [whenNothingStored] gives the answer for a project with no stored base
 * directory, in place of [Project.guessProjectDir].
 *
 * A JetBrains Client and the JetBrains Light product open the project on a local store directory, so
 * [Project.getBasePath] names that store directory and not the directory that the user opened. They store the user's
 * directory with [setRemoteProjectBaseNioPath], or an empty string when the project has no directory on the host. A
 * regular IDE and a remote development backend store nothing.
 *
 * This function exists for code that must keep its own answer when nothing is stored, for example
 * [Project.getBasePath]. The fallback of [getRemoteProjectBaseNioPath] is [Project.guessProjectDir]: the first top-level
 * content root, which is not always the directory of [Project.getBasePath]. With it, such code would get another
 * directory in a regular IDE than before.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
@VisibleToClasses("com.intellij.air.shared.core.AirProjectPathsKt")
fun Project.getRemoteProjectBaseNioPath(whenNothingStored: Project.() -> Path?): Path? {
  // The backend-provided path is a string in the host's path syntax, so it must be parsed
  // through EelPath to be routed via MultiRoutingFileSystem to the correct environment.
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
    return whenNothingStored()
  }

  return parseStoredHostPath(basePathFromBackend)
}

/**
 * Empty string is the "checked-absent" sentinel; the absence of any call is "not yet set"
 * and treated as an init-order error on a thin client.
 *
 * The identity is the host-side project key used to open/reopen the project, and may be a project
 * file rather than [getRemoteProjectBaseNioPath]'s directory. For example, Rider uses the `.sln`
 * path as project identity while the base path is the containing workspace root.
 */
@ApiStatus.Internal
fun Project.setRemoteProjectIdentityNioPath(rawHostPath: String) {
  putUserData(REMOTE_PROJECT_IDENTITY_PATH_KEY, rawHostPath)
}

/**
 * Returns the project's identity path as a [Path].
 *
 * On a thin client the backend-provided host path is routed through `MultiRoutingFileSystem`.
 * On a monolith it falls back to [RecentProjectsManager.getProjectPath], then to [Project.guessProjectDir]
 * for products that do not expose a separate project identity.
 */
@ApiStatus.Internal
fun Project.getRemoteProjectIdentityNioPath(): Path? {
  return getRemoteProjectIdentityNioPath(whenNothingStored = {
    RecentProjectsManager.getInstance().getProjectPath(this) ?: guessProjectDir()?.toNioPathOrNull()
  })
}

/**
 * Same as [getRemoteProjectIdentityNioPath], but [whenNothingStored] gives the answer for a project with no stored
 * identity path, in place of [RecentProjectsManager.getProjectPath] and [Project.guessProjectDir].
 *
 * It exists for the reason that the [getRemoteProjectBaseNioPath] function with `whenNothingStored` explains.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
@VisibleToClasses("com.intellij.air.shared.core.AirProjectPathsKt")
fun Project.getRemoteProjectIdentityNioPath(whenNothingStored: Project.() -> Path?): Path? {
  val identityPathFromBackend = getUserData(REMOTE_PROJECT_IDENTITY_PATH_KEY)

  if (identityPathFromBackend == null) {
    if (PlatformUtils.isJetBrainsClient() && getEelDescriptor() !is LocalEelDescriptor) {
      LOG.error("REMOTE_PROJECT_IDENTITY_PATH_KEY not set on thin client; init order is broken")
      return null
    }
    return whenNothingStored()
  }

  return parseStoredHostPath(identityPathFromBackend)
}

/**
 * Parses a path that the backend or the light product stored in the path syntax of the host, so that it routes through
 * `MultiRoutingFileSystem` to the right environment. An empty string is the "checked-absent" sentinel and gives `null`.
 */
private fun Project.parseStoredHostPath(hostPath: String): Path? {
  if (hostPath.isEmpty()) {
    // Checked-absent sentinel.
    return null
  }

  val descriptor = getEelDescriptor()

  return try {
    EelPath.parse(hostPath, descriptor).asNioPath()
  }
  catch (e: EelPathException) {
    LOG.error("Path '$hostPath' inconsistent with descriptor $descriptor", e)
    null
  }
}
