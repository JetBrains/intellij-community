// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.CommonBundle
import com.intellij.core.CoreBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService
import com.intellij.ide.plugins.marketplace.PluginSignatureChecker
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum
import com.intellij.ide.plugins.newui.PluginManagerSession
import com.intellij.ide.plugins.newui.PluginManagerSessionService
import com.intellij.ide.startup.StartupActionScriptManager.ActionCommand
import com.intellij.ide.startup.StartupActionScriptManager.CopyCommand
import com.intellij.ide.startup.StartupActionScriptManager.DeleteCommand
import com.intellij.ide.startup.StartupActionScriptManager.UnzipCommand
import com.intellij.ide.startup.StartupActionScriptManager.addActionCommands
import com.intellij.ide.startup.StartupActionScriptManager.addActionCommandsToBeginning
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ex.MessagesEx
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.Decompressor
import com.intellij.util.io.zip.JBZipFile
import com.intellij.util.ui.EDT
import com.intellij.util.ui.IoErrorText
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.function.Consumer
import javax.swing.JComponent

object PluginInstaller {
  private val LOG = Logger.getInstance(PluginInstaller::class.java)

  private val DROP_DISABLED_FLAG_OF_REINSTALLED_PLUGINS =
    SystemProperties.getBooleanProperty("plugins.drop-disabled-flag-of-uninstalled-plugins", true)

  internal const val UNKNOWN_HOST_MARKER: String = "__unknown_repository__"

  @JvmField
  internal val ourLock: Any = Any()

  /**
   * @return true if restart is needed
   */
  @ApiStatus.Internal
  @JvmStatic
  @Throws(IOException::class)
  fun prepareToUninstall(pluginDescriptor: IdeaPluginDescriptorImpl): Boolean {
    synchronized(ourLock) {
      if (PluginManagerCore.isPluginInstalled(pluginDescriptor.getPluginId())) {
        if (pluginDescriptor.isBundled) {
          throw IllegalArgumentException("Plugin is bundled: " + pluginDescriptor.getPluginId())
        }
        else {
          val checkNeedsRestart = { !DynamicPlugins.checkCanUnloadWithoutRestart(pluginDescriptor.getMainDescriptor()) }
          val needRestart = PluginManagerCore.isLoaded(pluginDescriptor) && if (EDT.isCurrentThreadEdt()) { // FIXME this is an ad-hoc fix to run BGT method on EDT
            runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
              checkNeedsRestart()
            }
          } else {
            checkNeedsRestart()
          }
          if (needRestart) {
            uninstallAfterRestart(pluginDescriptor)
          }
          PluginStateManager.fireState(pluginDescriptor, false)
          return needRestart
        }
      }
    }
    return false
  }

  @ApiStatus.Internal
  @JvmStatic
  @Throws(IOException::class)
  fun uninstallAfterRestart(pluginDescriptor: IdeaPluginDescriptor) {
    if (pluginDescriptor.isBundled) {
      throw IllegalArgumentException("Plugin is bundled: " + pluginDescriptor.getPluginId())
    }
    LOG.debug("Scheduling uninstallation of plugin $pluginDescriptor after restart")
    // Make sure this method does not interfere with installAfterRestart by adding the DeleteCommand to the beginning of the script.
    // This way plugin installation always takes place after plugin uninstallation.
    addActionCommandsToBeginning(listOf(DeleteCommand(pluginDescriptor.getPluginPath())))
  }

  @ApiStatus.Internal
  @JvmStatic
  fun unloadDynamicPlugin(
    parentComponent: JComponent?,
    pluginDescriptor: PluginMainDescriptor,
    isUpdate: Boolean,
  ): Boolean {
    val options = DynamicPlugins.UnloadPluginOptions().withDisable(false).withWaitForClassloaderUnload(true).withUpdate(isUpdate)
    return if (parentComponent != null) {
      DynamicPlugins.unloadPluginWithProgress(null, parentComponent, pluginDescriptor, options)
    }
    else {
      DynamicPlugins.unloadPlugin(pluginDescriptor, options)
    }
  }

  @ApiStatus.Internal
  @JvmStatic
  fun uninstallDynamicPlugin(
    parentComponent: JComponent?,
    pluginDescriptor: PluginMainDescriptor,
    isUpdate: Boolean,
  ): Boolean {
    if (pluginDescriptor.isBundled()) {
      throw IllegalArgumentException("Plugin is bundled: " + pluginDescriptor.getPluginId())
    }

    var uninstalledWithoutRestart = !pluginDescriptor.isEnabled() || unloadDynamicPlugin(parentComponent, pluginDescriptor, isUpdate)
    if (uninstalledWithoutRestart) {
      try {
        LOG.debug("Deleting dynamic plugin from disk: " + pluginDescriptor.getPluginPath())
        @Suppress("UseOptimizedEelFunctions")
        NioFiles.deleteRecursively(pluginDescriptor.getPluginPath())
      }
      catch (e: IOException) {
        LOG.info("Failed to delete jar of dynamic plugin", e)
        uninstalledWithoutRestart = false
      }
    }

    if (!uninstalledWithoutRestart) {
      try {
        uninstallAfterRestart(pluginDescriptor)
      }
      catch (e: IOException) {
        LOG.error(e)
      }
    }
    return uninstalledWithoutRestart
  }

  @ApiStatus.Internal
  @JvmStatic
  @Throws(IOException::class)
  fun installAfterRestartAndKeepIfNecessary(
    newDescriptor: IdeaPluginDescriptor,
    newPluginPath: Path,
    oldPluginPath: Path?,
  ) {
    installAfterRestart(newDescriptor, newPluginPath, oldPluginPath, !keepArchive())
  }

  @ApiStatus.Internal
  @JvmStatic
  @Throws(IOException::class)
  fun installAfterRestart(
    descriptor: IdeaPluginDescriptor,
    sourceFile: Path,
    existingPlugin: Path?,
    deleteSourceFile: Boolean,
  ) {
    LOG.debug("Scheduling installation of plugin $descriptor after restart")
    val commands = ArrayList<ActionCommand>()

    if (existingPlugin != null) {
      commands.add(DeleteCommand(existingPlugin))
    }

    val pluginsPath = getPluginsPath()
    if (sourceFile.fileName.toString().endsWith(".jar")) {
      commands.add(CopyCommand(sourceFile, pluginsPath.resolve(sourceFile.fileName)))
    }
    else {
      // drops stale directory
      commands.add(DeleteCommand(pluginsPath.resolve(rootEntryName(sourceFile))))
      commands.add(UnzipCommand(sourceFile, pluginsPath))
    }

    if (deleteSourceFile) {
      commands.add(DeleteCommand(sourceFile))
    }

    addActionCommands(commands)

    PluginStateManager.fireState(descriptor, true)
  }

  private fun installWithoutRestart(sourceFile: Path, descriptor: IdeaPluginDescriptorImpl, parent: JComponent?): Path? {
    var result: Path?
    try {
      val task = object : Task.WithResult<Path, IOException>(null, parent, IdeBundle.message("progress.title.installing.plugin", descriptor.getName()), false) {
        @Throws(IOException::class)
        override fun compute(indicator: ProgressIndicator): Path {
          return unpackPlugin(sourceFile, getPluginsPath())
        }
      }
      result = ProgressManager.getInstance().run(task)
    }
    catch (throwable: Throwable) {
      LOG.warn("Plugin " + descriptor + " failed to install without restart. " + throwable.message, throwable)
      result = null
    }
    PluginStateManager.fireState(descriptor, true)
    return result
  }

  @JvmStatic
  @Throws(IOException::class)
  fun unpackPlugin(sourceFile: Path, targetPath: Path): Path {
    LOG.debug("Unpacking $sourceFile to $targetPath")
    val target: Path
    if (sourceFile.fileName.toString().endsWith(".jar")) {
      target = targetPath.resolve(sourceFile.fileName.toString())
      NioFiles.createDirectories(targetPath)
      Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING)
    }
    else {
      target = targetPath.resolve(rootEntryName(sourceFile))
      @Suppress("UseOptimizedEelFunctions")
      NioFiles.deleteRecursively(target)
      Decompressor.Zip(sourceFile).withZipExtensions().extract(targetPath)
    }
    return target
  }

  @JvmStatic
  @Throws(IOException::class)
  fun rootEntryName(zip: Path): String {
    JBZipFile(zip).use { zipFile ->
      for (zipEntry in zipFile.entries) {
        // we do not necessarily get a separate entry for the subdirectory when the file
        // in the ZIP archive is placed in a subdirectory, so we need to check if the slash is found anywhere in the path
        val name = zipEntry.name
        val i = name.indexOf('/')
        if (i > 0) {
          return name.substring(0, i)
        }
      }
    }

    throw IOException("Corrupted archive (no file entries): $zip")
  }

  @JvmStatic
  fun addStateListener(listener: PluginStateListener) {
    PluginStateManager.addStateListener(listener)
  }

  @ApiStatus.Internal
  @JvmStatic
  @RequiresEdt
  fun installFromDisk(
    model: InstalledPluginsTableModel,
    pluginEnabler: PluginEnabler,
    file: Path,
    project: Project?,
    parent: JComponent?,
    callback: Consumer<in PluginInstallCallbackData>,
  ) {
    try {
      val pluginDescriptor = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable { loadDescriptorFromArtifact(file, null) },
        IdeBundle.message("action.InstallFromDiskAction.progress.text"),
        true,
        project,
      )

      if (pluginDescriptor == null) {
        MessagesEx.showErrorDialog(parent,
                                   IdeBundle.message("dialog.message.fail.to.load.plugin.descriptor.from.file", file.fileName),
                                   CommonBundle.getErrorTitle())
        return
      }

      if (!PluginManagerMain.checkThirdPartyPluginsAllowed(listOf(pluginDescriptor))) {
        return
      }

      if (!PluginManagementPolicy.getInstance().canInstallPlugin(pluginDescriptor)) {
        val message = IdeBundle.message("dialog.message.plugin.is.not.allowed", pluginDescriptor.name)
        MessagesEx.showWarningDialog(parent, message, IdeBundle.message("dialog.title.install.plugin"))
        return
      }

      val ourState = InstalledPluginsState.getInstance()
      if (ourState.wasInstalled(pluginDescriptor.pluginId)) {
        val message = IdeBundle.message("dialog.message.plugin.was.already.installed", pluginDescriptor.name)
        MessagesEx.showWarningDialog(parent, message, IdeBundle.message("dialog.title.install.plugin"))
        return
      }

      val error = PluginManagerCore.checkBuildNumberCompatibility(pluginDescriptor, PluginManagerCore.buildNumber)
      if (error != null) {
        MessagesEx.showErrorDialog(parent, error.detailedMessage, CommonBundle.getErrorTitle())
        return
      }
      if (isBrokenPlugin(pluginDescriptor)) {
        val message = CoreBundle.message("plugin.loading.error.long.marked.as.broken", pluginDescriptor.name, pluginDescriptor.version)
        MessagesEx.showErrorDialog(parent, message, CommonBundle.getErrorTitle())
        return
      }

      val installedPlugin = PluginManagerCore.getPlugin(pluginDescriptor.pluginId)
      if (installedPlugin != null && ApplicationInfoEx.getInstanceEx().isEssentialPlugin(installedPlugin.getPluginId())) {
        val message = IdeBundle.message("dialog.message.plugin.core.part",
                                        pluginDescriptor.name,
                                        ApplicationNamesInfo.getInstance().fullProductName)
        MessagesEx.showErrorDialog(parent, message, CommonBundle.getErrorTitle())
        return
      }

      val previousVersion = installedPlugin?.getVersion()
      PluginManagerUsageCollector.pluginInstallationStarted(pluginDescriptor, InstallationSourceEnum.FROM_DISK, previousVersion)

      if (!PluginSignatureChecker.verifyIfRequired(pluginDescriptor, file, false, true)) {
        return
      }

      @Suppress("UsagesOfObsoleteApi")
      val task = object : Task.WithResult<Pair<PluginInstallOperation, IdeaPluginDescriptor?>, RuntimeException>(
        null,
        parent,
        IdeBundle.message("progress.title.checking.plugin.dependencies"),
        true,
      ) {
        override fun compute(indicator: ProgressIndicator): Pair<PluginInstallOperation, IdeaPluginDescriptor?> {
          val repositoryPlugins = CustomPluginRepositoryService.getInstance().getCustomRepositoryPlugins()
          val operation = PluginInstallOperation(emptyList(), repositoryPlugins, indicator, pluginEnabler)
          operation.setAllowInstallWithoutRestart(true)
          return if (operation.checkMissingDependencies(pluginDescriptor, MutablePluginInstallationModel())) {
            Pair(operation, operation.checkDependenciesAndReplacements(pluginDescriptor))
          }
          else {
            Pair.empty()
          }
        }
      }

      @Suppress("UsagesOfObsoleteApi")
      val pair = ProgressManager.getInstance().run(task)
      val operation = pair.getFirst()
      if (operation == null) {
        return
      }

      val oldFile = if (installedPlugin != null && !installedPlugin.isBundled) installedPlugin.getPluginPath() else null
      var isRestartRequired =
        oldFile != null ||
        operation.isRestartRequired ||
        runWithModalProgressBlocking( // FIXME this is an ad-hoc fix for accessing BGT-method from EDT
          parent?.let { ModalTaskOwner.component(it) } ?: project?.let { ModalTaskOwner.project(it) }
          ?: ModalTaskOwner.guess(),
          "",
          cancellation = TaskCancellation.nonCancellable()
        ) {
          !DynamicPlugins.checkCanLoadWithoutRestart(pluginDescriptor)
        }
      for (dynamicPluginInstall in operation.pendingDynamicPluginInstalls) {
        val installed = installAndLoadDynamicPlugin(dynamicPluginInstall.file, parent, dynamicPluginInstall.pluginDescriptor)
        if (!installed) {
          isRestartRequired = true
        }
      }

      if (isRestartRequired) {
        installAfterRestart(pluginDescriptor, file, oldFile, false)
      }
      ourState.onPluginInstall(pluginDescriptor, installedPlugin != null, isRestartRequired)

      val toDisable = pair.getSecond()
      if (toDisable != null) {
        // TODO[yole] unload and check for restart
        pluginEnabler.disable(setOf(toDisable))
      }

      val installedDependencies = operation.installedDependentPlugins
      val installedPlugins = ArrayList<IdeaPluginDescriptor>()
      installedPlugins.add(pluginDescriptor)
      for (plugin in installedDependencies) {
        installedPlugins.add(plugin.pluginDescriptor)
      }

      val installedDependencyIds = ContainerUtil.map2Set(installedPlugins) { plugin -> plugin.getPluginId() }
      val notInstalled = findNotInstalledPluginDependencies(pluginDescriptor.dependencies, model, installedDependencyIds)
      if (!notInstalled.isEmpty()) {
        val message = IdeBundle.message("dialog.message.plugin.depends.on.unknown.plugin",
                                        pluginDescriptor.name,
                                        notInstalled.size,
                                        StringUtil.join(notInstalled, ", "))
        MessagesEx.showWarningDialog(parent, message, IdeBundle.message("dialog.title.install.plugin"))
      }

      PluginManagerMain.suggestToEnableInstalledDependantPlugins(pluginEnabler, installedPlugins)

      if (!isRestartRequired) {
        val session: PluginManagerSession? = PluginManagerSessionService.getInstance().getSession(model.mySessionId.toString())
        session?.dynamicPluginsToInstall?.put(pluginDescriptor.pluginId, PendingDynamicPluginInstall(file, pluginDescriptor))
      }
      callback.accept(PluginInstallCallbackData(file, pluginDescriptor, isRestartRequired))
      for (callbackData in installedDependencies) {
        if (callbackData.pluginDescriptor.pluginId != pluginDescriptor.pluginId) {
          callback.accept(callbackData)
        }
      }

      if (file.toString().endsWith(".zip") && keepArchive()) {
        val tempFile = MarketplacePluginDownloadService.getPluginTempFile()
        Files.copy(file, tempFile, StandardCopyOption.REPLACE_EXISTING)
        MarketplacePluginDownloadService.renameFileToZipRoot(tempFile)
      }
    }
    catch (ex: IOException) {
      LOG.error(ex)
      MessagesEx.showErrorDialog(parent, IoErrorText.message(ex), CommonBundle.getErrorTitle())
    }
  }

  @ApiStatus.Internal
  @JvmStatic
  @RequiresEdt
  fun installAndLoadDynamicPlugin(file: Path, descriptor: IdeaPluginDescriptorImpl): Boolean {
    return installAndLoadDynamicPlugin(file, null, descriptor)
  }

  /**
   * @return {@code true} if the plugin was successfully installed without a restart, {@code false} if restart is required
   */
  @ApiStatus.Internal
  @JvmStatic
  @RequiresEdt
  fun installAndLoadDynamicPlugin(
    file: Path,
    parent: JComponent?,
    descriptor: IdeaPluginDescriptorImpl,
  ): Boolean {
    val targetFile = installWithoutRestart(file, descriptor, parent)
    if (targetFile == null) {
      return false
    }

    val targetDescriptor = loadDescriptor(targetFile, false, PluginXmlPathResolver.DEFAULT_PATH_RESOLVER)
    if (targetDescriptor == null) {
      return false
    }

    val targetPluginId = targetDescriptor.pluginId

    // FIXME this is a bad place to do this IJPL-190806; bundled plugin may be not unloaded at this point
    val loadedPlugin = PluginManagerCore.findPlugin(targetPluginId)
    if (loadedPlugin != null && PluginManagerCore.isLoaded(loadedPlugin)) {
      LOG.warn("Plugin $loadedPlugin is still loaded, restart is required") // FIXME IJPL-193781
      return false
    }

    val pluginSet = PluginManagerCore.getPluginSet()
    val contentModuleIdMap = pluginSet.buildContentModuleIdMap()
    val pluginMap = pluginSet.buildPluginIdMap()

    if (pluginRequiresUltimatePluginButItsDisabled(targetDescriptor, pluginMap, contentModuleIdMap)) {
      LOG.warn("Plugin $targetPluginId requires Ultimate plugin, but it's disabled")
      return false
    }

    if (DROP_DISABLED_FLAG_OF_REINSTALLED_PLUGINS && PluginEnabler.HEADLESS.isDisabled(targetPluginId)) {
      val wasInstalledBefore = pluginSet.isPluginInstalled(targetPluginId)
      if (!wasInstalledBefore) {
        // FIXME can't drop the disabled flag first because it's implementation filters ids against the current plugin set;
        //  so load first, then enable
        targetDescriptor.isMarkedForLoading = true
        val result = DynamicPlugins.loadPlugin(targetDescriptor)
        PluginEnabler.HEADLESS.enable(setOf(targetDescriptor))
        return result
      }
    }

    return PluginEnabler.HEADLESS.isDisabled(targetPluginId) || DynamicPlugins.loadPlugin(targetDescriptor)
  }

  private fun keepArchive(): Boolean {
    return !LoadingState.COMPONENTS_LOADED.isOccurred || RegistryManager.getInstance().`is`("ide.plugins.keep.archive")
  }

  private fun findNotInstalledPluginDependencies(
    dependencies: List<IdeaPluginDependency>,
    model: InstalledPluginsTableModel,
    installedDependencies: Set<PluginId>,
  ): Set<String> {
    val notInstalled = HashSet<String>()

    for (dependency in dependencies) {
      if (dependency.isOptional) continue

      val pluginId = dependency.pluginId
      if (installedDependencies.contains(pluginId) ||
          model.isLoaded(pluginId) ||
          PluginManagerCore.looksLikePlatformPluginAlias(pluginId) ||
          PluginManagerCore.findPluginByPlatformAlias(pluginId) != null) {
        continue
      }

      notInstalled.add(pluginId.idString)
    }

    return notInstalled
  }

  private fun getPluginsPath(): Path {
    return Path.of(PathManager.getPluginsPath())
  }

  @ApiStatus.Internal
  @JvmStatic
  @RequiresEdt
  fun installPluginFromCallbackData(callbackData: PluginInstallCallbackData) {
    val descriptor = callbackData.pluginDescriptor
    val file = callbackData.file
    if (descriptor is IdeaPluginDescriptorImpl && file != null) {
      if (callbackData.restartNeeded) {
        shutdownOrRestartAppAfterInstall(descriptor)
      }
      else {
        val loaded = installAndLoadDynamicPlugin(file, descriptor)
        if (!loaded) {
          shutdownOrRestartAppAfterInstall(descriptor)
        }
      }
    }
  }

  private fun shutdownOrRestartAppAfterInstall(descriptor: IdeaPluginDescriptorImpl) {
    PluginManagerConfigurable.shutdownOrRestartAppAfterInstall(
      PluginManagerConfigurable.getUpdatesDialogTitle()
    ) { action ->
      IdeBundle.message(
        "plugin.installed.ide.restart.required.message",
        descriptor.getName(),
        action,
        ApplicationNamesInfo.getInstance().fullProductName
      )
    }
  }
}
