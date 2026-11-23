// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.fileChooser

import com.intellij.core.CoreBundle
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.universal.FileWatcherAdapter
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.Companion.getFilteredSystemRoots
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.MountStatus
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.Presentation
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.Root
import com.intellij.openapi.vfs.impl.wsl.WslConstants
import com.intellij.platform.eel.impl.fileChooser.EelFileWatcherAdapter
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.impl.wsl.WslEelDescriptor
import com.intellij.ui.BadgeIconSupplier
import com.intellij.util.system.WindowsRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import java.util.TreeSet
import kotlin.io.path.pathString

internal class WslFileChooserContributor : UniversalFileChooserContributor {

  override val tabTitle: String = "WSL"

  override suspend fun getRoots(): List<Root> = withContext(Dispatchers.IO) {
    val systemRoots = getFilteredSystemRoots { ownsPath(it) }
    val systemRootPaths = systemRoots.mapNotNull { it.path }

    val distributions = runCatching {
      withContext(Dispatchers.IO) {
        WslDistributionManager.getInstance().installedDistributionsFuture.await()
      }
    }.onFailure {
      if (it is CancellationException) {
        ensureActive()
      }
    }.getOrDefault(emptyList())
    val busyNames = withContext(Dispatchers.IO) { loadBusyDistributionNames() }

    val roots = mutableListOf<Root>()
    val handledIds = mutableSetOf<String>()

    for (distribution in distributions) {
      val uncRoot = distribution.getUNCRootPath()
      val id = canonicalRootId(uncRoot.pathString)
      handledIds += id
      if (distribution.msId in busyNames) continue

      val isMounted = WslIjentManager.getInstance().isIjentInitialized(distribution)
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
      val id = canonicalRootId(path.pathString)
      if (id !in handledIds && distributionIdFromRootId(id) !in busyNames) {
        roots += root
      }
    }

    // Present the distributions in a stable, alphabetical order by name instead of the arbitrary
    // order reported by WslDistributionManager.
    roots.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.presentation.presentableName })
  }

  override suspend fun getFilteredRoots(path: Path): List<Root> {
    if (path.getEelDescriptor() is WslEelDescriptor) {
      return listOf(UniversalFileChooserContributor.asDefaultRoot(path.root))
    }
    return emptyList()
  }

  override fun ownsPath(path: Path): Boolean = path.getEelDescriptor() is WslEelDescriptor

  override suspend fun getMountStatus(path: Path): MountStatus = withContext(Dispatchers.IO) {
    val descriptor = path.getEelDescriptor() as? WslEelDescriptor ?: return@withContext MountStatus.Unmounted
    if (WslIjentManager.getInstance().isIjentInitialized(descriptor.distribution)) MountStatus.Mounted else MountStatus.Unmounted
  }

  override suspend fun mount(path: Path) {
    val descriptor = path.getEelDescriptor() as? WslEelDescriptor ?: return
    ensureIjentInitialized(descriptor.distribution)
  }

  override suspend fun mountVirtualRoot(virtualRoot: Root): Path? = withContext(Dispatchers.IO) {
    val distributionId = distributionIdFromRootId(virtualRoot.id) ?: return@withContext null
    val distribution = WslDistributionManager.getInstance().getOrCreateDistributionByMsId(distributionId)
    ensureIjentInitialized(distribution)
    runCatching { Path.of(virtualRoot.id) }.getOrNull()
  }

  override fun getFileWatcherAdapter(): FileWatcherAdapter = EelFileWatcherAdapter()

  override suspend fun getPresentation(path: Path): Presentation? = withContext(Dispatchers.IO) {
    val descriptor = path.getEelDescriptor() as? WslEelDescriptor ?: return@withContext null
    val isMounted = WslIjentManager.getInstance().isIjentInitialized(descriptor.distribution)
    Presentation(
      presentableName = descriptor.distribution.presentableName,
      icon = if (isMounted) MOUNTED_DISTRIBUTION_ICON else AllIcons.RunConfigurations.Wsl,
    )
  }

  override fun getCustomLoadingText(): @Nls String {
    return CoreBundle.message("file.chooser.loading.the.installed.wsl.distributions.list")
  }

  private suspend fun ensureIjentInitialized(distribution: WSLDistribution) {
    withContext(Dispatchers.IO) {
      WslIjentManager.instanceAsync().getIjentApi(null, distribution, null, false)
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
    for (prefix in WSL_UNC_PREFIXES) {
      if (normalized.startsWith(prefix)) {
        return WSL_LOCALHOST_UNC_PREFIX + normalized.removePrefix(prefix)
      }
    }
    return normalized
  }

  /**
   * Returns the names of the distributions that WSL uninstalls or converts now.
   * The `State` registry value does not depend on the locale, unlike the `STATE` column of `wsl --list --verbose`.
   */
  private fun loadBusyDistributionNames(): Set<String> {
    val names = TreeSet(String.CASE_INSENSITIVE_ORDER)
    try {
      val guids = WindowsRegistry.subKeys(WindowsRegistry.Hive.CURRENT_USER, LXSS_KEY) ?: return names
      for (guid in guids) {
        val key = "$LXSS_KEY\\$guid"
        val state = WindowsRegistry.getInt(WindowsRegistry.Hive.CURRENT_USER, key, "State") ?: continue
        if (state !in BUSY_STATES) continue
        LOG.debug("Busy state found for: ${key}")
        val name = WindowsRegistry.getString(WindowsRegistry.Hive.CURRENT_USER, key, "DistributionName") ?: continue
        LOG.info("${name} is in busy state ${state}")
        names += name
      }
    }
    catch (e: WindowsRegistry.RegistryException) {
      LOG.warn("Cannot read the states of the WSL distributions", e)
    }
    return names
  }

  private fun distributionIdFromRootId(id: String): String? {
    val normalized = id.replace('/', '\\').trimEnd('\\')
    for (prefix in WSL_UNC_PREFIXES) {
      if (normalized.startsWith(prefix, ignoreCase = true)) {
        val rest = normalized.substring(prefix.length)
        return rest.substringBefore('\\').takeIf { it.isNotEmpty() }
      }
    }
    return null
  }
}

private val LOG = logger<WslFileChooserContributor>()

/** The default UNC prefix of the WSL file system. */
private const val WSL_LOCALHOST_UNC_PREFIX = "\\\\wsl.localhost\\"

/** The UNC prefixes of the WSL file system: the default one, then the legacy [WslConstants.UNC_PREFIX]. */
private val WSL_UNC_PREFIXES = listOf(WSL_LOCALHOST_UNC_PREFIX, WslConstants.UNC_PREFIX)

private const val LXSS_KEY = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Lxss"

/** The `State` registry values of a distribution that WSL uninstalls (3) or converts to another WSL version (4). */
private val BUSY_STATES = setOf(3, 4)

/** Linux (WSL) icon with a green live-indicator dot, shown for mounted (initialized) WSL distributions. */
private val MOUNTED_DISTRIBUTION_ICON = BadgeIconSupplier(AllIcons.Linux.Linux).liveIndicatorIcon
