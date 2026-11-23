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
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Utilities for deploying server files and directories to an Eel execution host. */
@ApiStatus.Experimental
@Deprecated("Use EelPathUtils.transferLocalContentToRemote with EelSystemFolderUtils.createTransferTarget or EelPathUtils.TransferTarget.Explicit instead")
object EelServerDeploymentUtils {
  private const val SERVER_DEPLOYMENTS_DIRECTORY = "server-deployments"
  @Suppress("DEPRECATION")
  private val LOG = logger<EelServerDeploymentUtils>()

  /**
   * Returns [pathToCopy] for a local target.
   * For a remote target, returns the path to the copied file or directory.
   *
   * For an identified plugin, uses `server-deployments/<plugin.id>/<plugin.version>/<filename>` under the system directory.
   * Sanitizes the plugin ID and version for use as directory names.
   *
   * Call this method directly from the plugin that owns [pathToCopy] to identify that plugin.
   * If the plugin cannot be identified, uses a cached temporary destination via [EelPathUtils.transferLocalContentToRemote].
   *
   * [pathToCopy] must point to a local file or directory.
   *
   * @throws IOException if the transfer or a file operation fails.
   */
  @Deprecated(
    "Use EelPathUtils.transferLocalContentToRemote with EelSystemFolderUtils.createTransferTarget or EelPathUtils.TransferTarget.Explicit instead",
  )
  @Suppress("unused")
  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  @Throws(IOException::class)
  fun findOrDeployServer(target: EelDescriptor, pathToCopy: Path): Path {
    require(Files.exists(pathToCopy)) { "Server path must exist: $pathToCopy" }

    if (target === LocalEelDescriptor) {
      LOG.debug { "Using local server path: $pathToCopy" }
      return pathToCopy
    }

    val callerClass = ReflectionUtil.getCallerClass(3)
    val plugin = (callerClass?.classLoader as? PluginAwareClassLoader)?.pluginDescriptor
    val transferTarget = if (plugin == null) {
      LOG.warn("Cannot identify the calling plugin; deploying $pathToCopy to a temporary location")
      TransferTarget.Temporary(target)
    }
    else {
      val pluginVersion = checkNotNull(plugin.version) { "Cannot determine the version of plugin ${plugin.pluginId}" }
      val relativeDirectory = Path.of(
        SERVER_DEPLOYMENTS_DIRECTORY,
        sanitizeFileName(plugin.pluginId.idString),
        sanitizeFileName(pluginVersion),
      )
      EelSystemFolderUtils.createTransferTarget(target, relativeDirectory)
    }

    return EelPathUtils.transferLocalContentToRemote(
      source = pathToCopy,
      target = transferTarget,
    ).also {
      LOG.info("Successfully deployed server from $pathToCopy to $it")
    }
  }
}
