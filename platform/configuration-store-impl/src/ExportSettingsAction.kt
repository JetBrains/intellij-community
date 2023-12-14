// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ImportSettingsFilenameFilter
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showOkCancelDialog
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.ArrayUtil
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.putValue
import com.intellij.util.io.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

// for Rider purpose
open class ExportSettingsAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend, DumbAware {
  protected open fun getExportableComponents(): Map<FileSpec, List<ExportableItem>> = filterExisting(getExportableComponentsMap(true))

  protected open fun exportSettings(saveFile: Path, markedComponents: Set<ExportableItem>) {
    saveFile.outputStream().use {
      exportSettings(markedComponents, it)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = true
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    ApplicationManager.getApplication().saveSettings()

    val dialog = ChooseComponentsToExportDialog(getExportableComponents(), true,
                                                ConfigurationStoreBundle.message("title.select.components.to.export"),
                                                ConfigurationStoreBundle.message("prompt.please.check.all.components.to.export"))
    if (!dialog.showAndGet()) {
      return
    }

    val markedComponents = dialog.exportableComponents
    if (markedComponents.isEmpty()) {
      return
    }

    val saveFile = dialog.exportFile
    try {
      if (saveFile.exists() && showOkCancelDialog(
          title = IdeBundle.message("title.file.already.exists"),
          message = ConfigurationStoreBundle.message("prompt.overwrite.settings.file", saveFile.toString()),
          okText = IdeBundle.message("action.overwrite"),
          icon = Messages.getWarningIcon()) != Messages.OK) {
        return
      }

      exportSettings(saveFile, markedComponents)
      if (showOkCancelDialog(
          title = ConfigurationStoreBundle.message("title.export.successful"),
          message = ConfigurationStoreBundle.message("message.settings.exported.successfully"),
          okText = RevealFileAction.getActionName(null),
          cancelText = IdeBundle.message("action.close"),
          icon = Messages.getInformationIcon(),
          project = getEventProject(e)) == Messages.OK) {
        RevealFileAction.openFile(saveFile.toFile())
      }
    }
    catch (e: IOException) {
      Messages.showErrorDialog(ConfigurationStoreBundle.message("error.writing.settings", e.toString()),
                               IdeBundle.message("title.error.writing.file"))
    }
  }

  private fun filterExisting(exportableComponents: Map<FileSpec, List<ExportableItem>>): Map<FileSpec, List<ExportableItem>> {
    return exportableComponents.mapNotNull { (fileSpec, items) ->
      val existingItems = items.filter { exists(it) }
      if (existingItems.isEmpty()) null
      else fileSpec to existingItems
    }.toMap()
  }

  private fun exists(item: ExportableItem): Boolean {
    if (item.fileSpec.isDirectory) {
      return checkIfDirectoryExists(item, getAppStorageManager())
    }
    else {
      val content = loadFileContent(item, getAppStorageManager())
      return content != null && isComponentDefined(item.componentName, content)
    }
  }

}

fun exportSettings(exportableItems: Set<ExportableItem>,
                   out: OutputStream,
                   exportableThirdPartyFiles: Map<FileSpec, Path> = mapOf(),
                   storageManager: StateStorageManagerImpl = getAppStorageManager()) {
  val filter = HashSet<String>()
  Compressor.Zip(out)
    .filter { entryName, _ -> filter.add(entryName) }
    .use { zip ->
      for (item in exportableItems) {
        if (item.fileSpec.isDirectory) {
          exportDirectory(item, zip, storageManager)
        }
        else {
          val content = loadFileContent(item, storageManager)
          if (content != null) {
            zip.addFile(item.fileSpec.relativePath, content)
          }
        }
      }

      // dotSettings file for Rider backend
      for ((fileSpec, path) in exportableThirdPartyFiles) {
        LOG.assertTrue(!fileSpec.isDirectory, "fileSpec should not be directory")
        LOG.assertTrue(path.isRegularFile(), "path should be file")

        zip.addFile(fileSpec.relativePath, Files.readAllBytes(path))
      }

      exportInstalledPlugins(zip)

      zip.addFile(ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER, ArrayUtil.EMPTY_BYTE_ARRAY)
    }
}

@ApiStatus.Internal
data class FileSpec(@NlsSafe val relativePath: String,
                    /* File spec as written in the class annotation */ val rawFileSpec: String,
                    val isDirectory: Boolean = false)

@ApiStatus.Internal
data class ExportableItem(val fileSpec: FileSpec,
                          val presentableName: String,
                          @NonNls val componentName: String? = null,
                          val roamingType: RoamingType = RoamingType.DEFAULT)

@ApiStatus.Internal
data class LocalExportableItem(val file: Path, val presentableName: String, val roamingType: RoamingType = RoamingType.DEFAULT)

@ApiStatus.Internal
fun exportInstalledPlugins(zip: Compressor) {
  val pluginIds = PluginManagerCore.loadedPlugins
    .asSequence()
    .filterNot { it.isBundled }
    .joinToString("\n") { it.pluginId.idString }
  if (pluginIds.isNotEmpty()) {
    zip.addFile(PluginManager.INSTALLED_TXT, pluginIds.toByteArray())
  }
}

@ApiStatus.Internal
fun getExportableComponentsMap(isComputePresentableNames: Boolean,
                               storageManager: StateStorageManager = getAppStorageManager(),
                               withDeprecated: Boolean = false,
                               withExportable: Boolean = true): Map<FileSpec, List<ExportableItem>> {
  val result = LinkedHashMap<FileSpec, MutableList<ExportableItem>>()

  @Suppress("DEPRECATION")
  val processor = { component: ExportableComponent ->
    for (file in component.exportFiles) {
      val path = getRelativePathOrNull(file.toPath())
      if (path != null) {
        val fileSpec = FileSpec(relativePath = path, rawFileSpec = path, isDirectory = looksLikeDirectory(file.name))
        val item = ExportableItem(fileSpec, component.presentableName)
        result.putValue(fileSpec, item)
      }
    }
  }

  val app = ApplicationManager.getApplication() as ComponentManagerImpl

  if (withExportable) {
    @Suppress("DEPRECATION")
    ServiceBean.loadServicesFromBeans(ExportableComponent.EXTENSION_POINT, ExportableComponent::class.java).forEach(processor)
  }

  app.processAllImplementationClasses { aClass, pluginDescriptor ->
    val stateAnnotation = getStateSpec(aClass)
    @Suppress("DEPRECATION")
    if (stateAnnotation == null || stateAnnotation.name.isEmpty() || ExportableComponent::class.java.isAssignableFrom(aClass)) {
      return@processAllImplementationClasses
    }

    val storages = if (!withDeprecated) {
      stateAnnotation.storages.filter { !it.deprecated }
    }
    else {
      sortStoragesByDeprecated(stateAnnotation.storages.asList())
    }

    if (storages.isEmpty()) return@processAllImplementationClasses

    val presentableName = if (isComputePresentableNames) getComponentPresentableName(stateAnnotation, aClass, pluginDescriptor) else ""

    var thereIsExportableStorage = false
    for (storage in storages) {
      val isRoamable = getEffectiveRoamingType(storage.roamingType, storage.path).isRoamable
      val exportable = isStorageExportable(storage, isRoamable, withExportable)
      LOG.debug("Storage for class ${aClass.simpleName} is ${stringify(isRoamable, "roamable")}, ${stringify(exportable, "exportable")}: $storage")
      if (exportable) {
        thereIsExportableStorage = true
        val paths = getRelativePaths(storage, storageManager, withDeprecated)
        for (path in paths) {
          val fileSpec = FileSpec(relativePath = path, rawFileSpec = storage.path, isDirectory = looksLikeDirectory(storage))
          result.putValue(fileSpec, ExportableItem(fileSpec, presentableName, stateAnnotation.name, storage.roamingType))
        }
      }
    }

    if (thereIsExportableStorage) {
      val additionalExportFile = getAdditionalExportFile(stateAnnotation)
      if (additionalExportFile != null) {
        val additionalFileSpec = FileSpec(relativePath = additionalExportFile, rawFileSpec = additionalExportFile, isDirectory = true)
        result.putValue(additionalFileSpec, ExportableItem(additionalFileSpec, "$presentableName (schemes)"))
      }
    }
  }

  // must be in the end - because most of the SchemeManager clients specify additionalExportFile in the State spec
  (SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase).process {
    if (it.roamingType.isRoamable && it.fileSpec.getOrNull(0) != '$') {
      val fileSpec = FileSpec(relativePath = it.fileSpec, rawFileSpec = it.fileSpec, isDirectory = true)
      if (!result.containsKey(fileSpec)) {
        result.putValue(fileSpec, ExportableItem(fileSpec, it.presentableName ?: "", null, it.roamingType))
      }
    }
  }
  return result
}

private fun stringify(value: Boolean, name: String): String = if (value) name else "not $name"

fun looksLikeDirectory(storage: Storage): Boolean {
  return storage.stateSplitter.java != StateSplitterEx::class.java
}

private fun looksLikeDirectory(fileSpec: String) = !fileSpec.endsWith(PathManager.DEFAULT_EXT)

private fun getRelativePaths(storage: Storage, storageManager: StateStorageManager, withDeprecated: Boolean): List<String> {
  val storagePath = storage.path
  val relativePaths = mutableListOf<String>()
  if (storage.roamingType == RoamingType.PER_OS) {
    relativePaths += getOsDependentStorage(storagePath)
    if (withDeprecated) {
      relativePaths += storagePath
    }
  }
  else {
    relativePaths += storagePath
  }

  return relativePaths.map {
    val expandedPath = storageManager.expandMacro(it)
    getRelativePathOrNull(expandedPath) ?: expandedPath.toString()
  }
}

private fun getRelativePathOrNull(fullPath: Path): String? {
  val configPath = PathManager.getConfigDir()
  if (fullPath.startsWith(configPath)) {
    return configPath.relativize(fullPath).systemIndependentPath
  }
  return null
}

private fun getAdditionalExportFile(stateAnnotation: State) = stateAnnotation.additionalExportDirectory.takeIf { it.isNotEmpty() }

private fun getAppStorageManager() = ApplicationManager.getApplication().stateStore.storageManager as StateStorageManagerImpl

private fun isStorageExportable(storage: Storage, isRoamable: Boolean, withExportable: Boolean): Boolean =
  storage.exportable && withExportable ||
  isRoamable && storage.storageClass == StateStorage::class && storage.path.isNotEmpty()

private fun getComponentPresentableName(state: State, aClass: Class<*>, pluginDescriptor: PluginDescriptor?): String {
  val presentableName = state.presentableName.java
  if (presentableName != State.NameGetter::class.java) {
    try {
      return ReflectionUtil.newInstance(presentableName).get()
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  val defaultName = state.name

  fun trimDefaultName(): String {
    // Vcs.Log.App.Settings
    return defaultName
      .removeSuffix(".Settings")
      .removeSuffix(".Settings")
  }

  var resourceBundleName: String?
  if (pluginDescriptor != null && PluginManagerCore.CORE_ID != pluginDescriptor.pluginId) {
    resourceBundleName = pluginDescriptor.resourceBundleBaseName
    if (resourceBundleName == null) {
      if (pluginDescriptor.vendor == "JetBrains") {
        resourceBundleName = OptionsBundle.BUNDLE
      }
      else {
        return trimDefaultName()
      }
    }
  }
  else {
    resourceBundleName = OptionsBundle.BUNDLE
  }

  val classLoader = pluginDescriptor?.pluginClassLoader ?: aClass.classLoader
  if (classLoader != null) {
    val message = messageOrDefault(classLoader, resourceBundleName, defaultName)
    if (message !== defaultName) {
      return message
    }
  }
  return trimDefaultName()
}

private fun messageOrDefault(classLoader: ClassLoader, bundleName: String, @Nls defaultName: String): String {
  try {
    return AbstractBundle.messageOrDefault(
      DynamicBundle.getResourceBundle(classLoader, bundleName), "exportable.$defaultName.presentable.name", defaultName)
  }
  catch (e: MissingResourceException) {
    LOG.warn("Missing bundle ${bundleName} at ${classLoader}: ${e.message}")
    return defaultName
  }
}

fun getExportableItemsFromLocalStorage(exportableItems: Map<FileSpec, List<ExportableItem>>, storageManager: StateStorageManager):
  Map<Path, List<LocalExportableItem>> {

  return exportableItems.entries.mapNotNull { (fileSpec, items) ->
    getLocalPath(fileSpec, storageManager)?.let { path ->
      val localItems = items.map { LocalExportableItem(path, it.presentableName, it.roamingType) }
      path to localItems
    }
  }.toMap()
}

private fun getLocalPath(fileSpec: FileSpec, storageManager: StateStorageManager) =
  storageManager.expandMacro(ROOT_CONFIG).resolve(fileSpec.relativePath).takeIf { it.exists() }

private fun loadFileContent(item: ExportableItem, storageManager: StateStorageManagerImpl): ByteArray? {
  var content: ByteArray? = null
  var errorDuringLoadingFromProvider = false
  val skipProvider = !item.roamingType.isRoamable
  val handledByProvider = !skipProvider && storageManager.compoundStreamProvider.read(item.fileSpec.rawFileSpec,
                                                                                      item.roamingType) { inputStream ->
    // null stream means empty file which shouldn't be exported
    inputStream?.let {
      try {
        content = FileUtil.loadBytes(inputStream)
      }
      catch (e: Exception) {
        LOG.warn(e)
        errorDuringLoadingFromProvider = true
      }
    }
  }

  if (!handledByProvider || errorDuringLoadingFromProvider) {
    val path = getLocalPath(item.fileSpec, storageManager)
    if (path != null) {
      val bytes = Files.readAllBytes(path)
      if (isComponentDefined(item.componentName, bytes)) {
        content = bytes
      }
    }
  }

  return content
}

private fun isComponentDefined(componentName: String?, bytes: ByteArray): Boolean {
  return componentName == null || bytes.decodeToString().contains("""<component name="${componentName}"""")
}

private fun exportDirectory(item: ExportableItem, zip: Compressor, storageManager: StateStorageManagerImpl) {
  var error = false
  val success = storageManager.compoundStreamProvider.processChildren(item.fileSpec.relativePath, item.roamingType,
                                                                      { true }) { name: String, inputStream: InputStream, _: Boolean ->
    try {
      val fileName = item.fileSpec.relativePath + "/" + name
      zip.addFile(fileName, inputStream)
      true
    }
    catch (e: Exception) {
      LOG.warn(e)
      error = true
      false
    }
  }

  if (!success || error) {
    val localPath = getLocalPath(item.fileSpec, storageManager)
    if (localPath != null) {
      zip.addDirectory(item.fileSpec.relativePath, localPath)
    }
  }
}

private fun checkIfDirectoryExists(item: ExportableItem, storageManager: StateStorageManagerImpl): Boolean {
  var exists = false
  val handledByProvider = storageManager.compoundStreamProvider.processChildren(item.fileSpec.relativePath, item.roamingType,
                                                                                { true }) { _, _, _ ->
    exists = true
    false // stop processing children: now we know that the directory exists and is not empty
  }

  if (handledByProvider) {
    return exists
  }
  else {
    val localPath = getLocalPath(item.fileSpec, storageManager)
    return localPath != null && localPath.exists()
  }
}
