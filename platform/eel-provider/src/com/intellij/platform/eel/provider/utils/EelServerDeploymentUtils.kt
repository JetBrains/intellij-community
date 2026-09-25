// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils.TransferTarget
import com.intellij.util.ReflectionUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.sanitizeFileName
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

/** Utilities for deploying server files and directories to an Eel execution host. */
@ApiStatus.Experimental
object EelServerDeploymentUtils {
  private const val SERVER_DEPLOYMENTS_DIRECTORY = "server-deployments"
  private val LOG = logger<EelServerDeploymentUtils>()

  /**
   * Returns [pathToCopy] for a local target.
   * For a remote target, returns the path to the copied file or directory.
   *
   * For an identified plugin, uses `server-deployments/<plugin.id>/<plugin.version>/<filename>` under the system directory.
   * Sanitizes the plugin ID and version for use as directory names.
   * Reuses the copy only when the destination and its completion marker both exist.
   * Creates the sibling `<filename>.deployed` marker only after the transfer succeeds.
   *
   * Call this method directly from the plugin that owns [pathToCopy] to identify that plugin.
   * If the plugin cannot be identified, uses a cached temporary destination via [EelPathUtils.transferLocalContentToRemote].
   *
   * [pathToCopy] must point to a local file or directory.
   */
  @Suppress("unused")
  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun findOrDeployServer(target: EelDescriptor, pathToCopy: Path): Path {
    require(Files.exists(pathToCopy)) { "Server path must exist: $pathToCopy" }

    if (target === LocalEelDescriptor) {
      LOG.debug { "Using local server path: $pathToCopy" }
      return pathToCopy
    }

    val callerClass = ReflectionUtil.getCallerClass(3)
    val plugin = (callerClass?.classLoader as? PluginAwareClassLoader)?.pluginDescriptor
    if (plugin == null) {
      LOG.warn("Cannot identify the calling plugin; deploying $pathToCopy to a temporary location")
      return EelPathUtils.transferLocalContentToRemote(
        source = pathToCopy,
        target = TransferTarget.Temporary(target),
      ).also {
        LOG.info("Successfully deployed server from $pathToCopy to $it")
      }
    }

    val pluginVersion = checkNotNull(plugin.version) { "Cannot determine the version of plugin ${plugin.pluginId}" }

    val deployedServerPath = EelSystemFolderUtils.getSystemFolder(target)
      .resolve(SERVER_DEPLOYMENTS_DIRECTORY)
      .resolve(sanitizeFileName(plugin.pluginId.idString))
      .resolve(sanitizeFileName(pluginVersion))
      .resolve(pathToCopy.fileName?.toString() ?: error("Server path must have a file or directory name: $pathToCopy"))
    val deploymentMarker = deployedServerPath.resolveSibling("${deployedServerPath.fileName}.deployed")

    if (Files.exists(deployedServerPath) && Files.isRegularFile(deploymentMarker)) {
      LOG.debug { "Reusing deployed server path: $deployedServerPath" }
      return deployedServerPath
    }

    LOG.debug { "Deploying server from $pathToCopy to $deployedServerPath" }
    Files.createDirectories(deployedServerPath.parent)
    Files.deleteIfExists(deploymentMarker)
    EelPathUtils.transferLocalContentToRemote(
      source = pathToCopy,
      target = TransferTarget.Explicit(deployedServerPath),
    )
    Files.createFile(deploymentMarker)
    LOG.info("Successfully deployed server from $pathToCopy to $deployedServerPath")
    return deployedServerPath
  }
}
