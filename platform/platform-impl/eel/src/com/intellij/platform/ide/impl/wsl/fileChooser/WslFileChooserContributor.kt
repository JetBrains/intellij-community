// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.fileChooser

import com.intellij.core.CoreBundle
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.universal.FileWatcherAdapter
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.Companion.getFilteredSystemRoots
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.MountStatus
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.Presentation
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.Root
import com.intellij.platform.eel.impl.fileChooser.EelFileWatcherAdapter
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.impl.wsl.WslEelDescriptor
import com.intellij.ui.BadgeIconSupplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.io.path.pathString

internal class WslFileChooserContributor : UniversalFileChooserContributor {

  override val tabTitle: String = "WSL"

  override suspend fun getRoots(): List<Root> = withContext(Dispatchers.IO) {
    val systemRoots = getFilteredSystemRoots { ownsPath(it) }
    val systemRootPaths = systemRoots.mapNotNull { it.path }

    val distributions = runCatching {
      WslDistributionManager.getInstance().installedDistributionsFuture.await()
    }.getOrDefault(emptyList())

    val roots = mutableListOf<Root>()
    val handledIds = mutableSetOf<String>()

    for (distribution in distributions) {
      val uncRoot = distribution.getUNCRootPath()
      val id = canonicalRootId(uncRoot.pathString)
      handledIds += id

      val isMounted = WslIjentManager.getInstance().isIjentInitialized(WslEelDescriptor(distribution))
      val mountedRealPath = systemRootPaths.firstOrNull { canonicalRootId(it.pathString) == id }

      if (isMounted && mountedRealPath != null) {
        roots += distributionRoot(distribution, id, mountedRealPath, isVirtual = false, isMounted = true)
      }
      else {
        // Distribution is not initialized yet — expose it as a virtual root that will be mounted on demand.
        roots += distributionRoot(distribution, id, uncRoot, isVirtual = true, isMounted = false)
      }
    }

    // Preserve any WSL system roots that were not reported by WslDistributionManager (e.g. cache mismatch).
    for (root in systemRoots) {
      val path = root.path ?: continue
      if (canonicalRootId(path.pathString) !in handledIds) {
        roots += root
      }
    }

    roots
  }

  override suspend fun getFilteredRoots(path: Path): List<Root> {
    if (path.getEelDescriptor() is WslEelDescriptor) {
      return listOf(UniversalFileChooserContributor.asDefaultRoot(path.root))
    }
    return emptyList()
  }

  override fun ownsPath(path: Path): Boolean = path.asEelPath().descriptor is WslEelDescriptor

  override suspend fun getMountStatus(path: Path): MountStatus = withContext(Dispatchers.IO) {
    val descriptor = path.asEelPath().descriptor as? WslEelDescriptor ?: return@withContext MountStatus.Unmounted
    if (WslIjentManager.getInstance().isIjentInitialized(descriptor)) MountStatus.Mounted else MountStatus.Unmounted
  }

  override suspend fun mount(path: Path) {
    val descriptor = path.asEelPath().descriptor as? WslEelDescriptor ?: return
    ensureIjentInitialized(descriptor)
  }

  override suspend fun mountVirtualRoot(virtualRoot: Root): Path? = withContext(Dispatchers.IO) {
    val distributionId = distributionIdFromRootId(virtualRoot.id) ?: return@withContext null
    val distribution = WslDistributionManager.getInstance().getOrCreateDistributionByMsId(distributionId)
    val descriptor = WslEelDescriptor(distribution)
    ensureIjentInitialized(descriptor)
    runCatching { Path.of(virtualRoot.id) }.getOrNull()
  }

  override fun getFileWatcherAdapter(): FileWatcherAdapter = EelFileWatcherAdapter()

  override suspend fun getPresentation(path: Path): Presentation? = withContext(Dispatchers.IO) {
    val descriptor = path.asEelPath().descriptor as? WslEelDescriptor ?: return@withContext null
    val isMounted = WslIjentManager.getInstance().isIjentInitialized(descriptor)
    Presentation(
      presentableName = descriptor.distribution.presentableName,
      icon = if (isMounted) MOUNTED_DISTRIBUTION_ICON else AllIcons.RunConfigurations.Wsl,
    )
  }

  override fun getCustomLoadingText(): @Nls String {
    return CoreBundle.message("file.chooser.loading.the.installed.wsl.distributions.list")
  }

  private suspend fun ensureIjentInitialized(descriptor: WslEelDescriptor) {
    withContext(Dispatchers.IO) {
      WslIjentManager.instanceAsync().getIjentApi(descriptor, descriptor.distribution, null, false)
    }
  }

  private fun distributionRoot(distribution: WSLDistribution, id: String, path: Path, isVirtual: Boolean, isMounted: Boolean): Root =
    Root(
      id = id,
      presentation = Presentation(
        presentableName = distribution.presentableName,
        icon = if (isMounted) MOUNTED_DISTRIBUTION_ICON else AllIcons.Linux.Linux,
      ),
      path = if (isVirtual) null else path,
    )

  /**
   * Normalises WSL UNC roots so that `\\wsl$\Ubuntu\` and `\\wsl.localhost\Ubuntu\` map to the same identifier.
   * Uses the `wsl.localhost` prefix as the canonical form, matching what `EelWslMrfsBackend.getCustomRoots` exposes.
   */
  private fun canonicalRootId(pathString: String): String {
    val normalized = pathString.replace('/', '\\').trimEnd('\\')
    val prefixes = listOf("\\\\wsl.localhost\\", "\\\\wsl\$\\")
    for (prefix in prefixes) {
      if (normalized.startsWith(prefix)) {
        return "\\\\wsl.localhost\\" + normalized.removePrefix(prefix)
      }
    }
    return normalized
  }

  private fun distributionIdFromRootId(id: String): String? {
    val normalized = id.replace('/', '\\').trimEnd('\\')
    val prefixes = listOf("\\\\wsl.localhost\\", "\\\\wsl\$\\")
    for (prefix in prefixes) {
      if (normalized.startsWith(prefix, ignoreCase = true)) {
        val rest = normalized.substring(prefix.length)
        return rest.substringBefore('\\').takeIf { it.isNotEmpty() }
      }
    }
    return null
  }
}


/** Linux (WSL) icon with a green live-indicator dot, shown for mounted (initialized) WSL distributions. */
private val MOUNTED_DISTRIBUTION_ICON = BadgeIconSupplier(AllIcons.Linux.Linux).liveIndicatorIcon
