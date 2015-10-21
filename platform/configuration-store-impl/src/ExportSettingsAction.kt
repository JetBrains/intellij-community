/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions

import com.intellij.AbstractBundle
import com.intellij.CommonBundle
import com.intellij.configurationStore.ROOT_CONFIG
import com.intellij.configurationStore.sortStoragesByDeprecated
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.ServiceManagerImpl
import com.intellij.openapi.components.impl.stores.StateStorageManager
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.PairProcessor
import com.intellij.util.PlatformUtils
import com.intellij.util.ReflectionUtil
import com.intellij.util.SmartList
import com.intellij.util.io.ZipUtil
import gnu.trove.THashSet
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private class ExportSettingsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent?) {
    ApplicationManager.getApplication().saveSettings()

    val dialog = ChooseComponentsToExportDialog(getExportableComponentsMap(true, true), true,
      IdeBundle.message("title.select.components.to.export"),
      IdeBundle.message(
        "prompt.please.check.all.components.to.export"))
    if (!dialog.showAndGet()) {
      return
    }

    val markedComponents = dialog.exportableComponents
    if (markedComponents.isEmpty()) {
      return
    }

    val exportFiles = THashSet(FileUtil.FILE_HASHING_STRATEGY)
    for (markedComponent in markedComponents) {
      exportFiles.addAll(markedComponent.files)
    }

    val saveFile = dialog.exportFile
    try {
      if (saveFile.exists() && Messages.showOkCancelDialog(
        IdeBundle.message("prompt.overwrite.settings.file", FileUtil.toSystemDependentName(saveFile.path)),
        IdeBundle.message("title.file.already.exists"), Messages.getWarningIcon()) != Messages.OK) {
        return
      }

      exportSettings(exportFiles, saveFile.outputStream().buffered(), FileUtilRt.toSystemIndependentName(PathManager.getConfigPath()))
      ShowFilePathAction.showDialog(AnAction.getEventProject(e), IdeBundle.message("message.settings.exported.successfully"),
        IdeBundle.message("title.export.successful"), saveFile, null)
    }
    catch (e1: IOException) {
      Messages.showErrorDialog(IdeBundle.message("error.writing.settings", e1.toString()), IdeBundle.message("title.error.writing.file"))
    }
  }
}

// not internal only to test
fun exportSettings(exportFiles: Set<File>, out: OutputStream, configPath: String) {
  val zipOut = MyZipOutputStream(out)
  try {
    val writtenItemRelativePaths = THashSet<String>()
    for (file in exportFiles) {
      if (file.exists()) {
        val relativePath = FileUtilRt.getRelativePath(configPath, FileUtilRt.toSystemIndependentName(file.absolutePath), '/')!!
        ZipUtil.addFileOrDirRecursively(zipOut, null, file, relativePath, null, writtenItemRelativePaths)
      }
    }

    exportInstalledPlugins(zipOut)

    val zipEntry = ZipEntry(ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER)
    zipOut.putNextEntry(zipEntry)
    zipOut.closeEntry()
  }
  finally {
    zipOut.doClose()
  }
}

private class MyZipOutputStream(out: OutputStream) : ZipOutputStream(out) {
  override fun close() {
  }

  fun doClose() {
    super.close()
  }
}

data class ExportableItem(val files: List<File>, val presentableName: String, val roamingType: RoamingType = RoamingType.DEFAULT)

private val LOG = Logger.getInstance(ExportSettingsAction::class.java)

private fun exportInstalledPlugins(zipOut: MyZipOutputStream) {
  val plugins = ArrayList<String>()
  for (descriptor in PluginManagerCore.getPlugins()) {
    if (!descriptor.isBundled && descriptor.isEnabled) {
      plugins.add(descriptor.pluginId.idString)
    }
  }
  if (plugins.isEmpty()) {
    return
  }

  val e = ZipEntry(PluginManager.INSTALLED_TXT)
  zipOut.putNextEntry(e)
  try {
    PluginManagerCore.writePluginsList(plugins, OutputStreamWriter(zipOut, CharsetToolkit.UTF8_CHARSET))
  }
  finally {
    zipOut.closeEntry()
  }
}

fun <K, V> MutableMap<K, MutableList<V>>.putValue(key: K, value: V) {
  var list = get(key)
  if (list == null) {
    list = SmartList<V>(value)
    put(key, list)
  }
  else {
    list.add(value)
  }
}

// onlyPaths - include only specified paths (relative to config dir, ends with "/" if directory)
fun getExportableComponentsMap(onlyExisting: Boolean,
                               computePresentableNames: Boolean,
                               storageManager: StateStorageManager = ApplicationManager.getApplication().stateStore.stateStorageManager,
                               onlyPaths: Set<String>? = null): Map<File, List<ExportableItem>> {
  val result = LinkedHashMap<File, MutableList<ExportableItem>>()
  val processor = { component: ExportableComponent ->
    val item = ExportableItem(component.exportFiles.toList(), component.presentableName, RoamingType.DEFAULT)
    for (exportFile in item.files) {
      result.putValue(exportFile, item)
    }
  }

  @Suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
  ApplicationManager.getApplication().getComponents(ExportableApplicationComponent::class.java).forEach(processor)
  ServiceBean.loadServicesFromBeans(ExportableComponent.EXTENSION_POINT, ExportableComponent::class.java).forEach(processor)

  val configPath = storageManager.expandMacros(ROOT_CONFIG)

  fun isSkipFile(file: File): Boolean {
    if (onlyPaths != null) {
      var relativePath = FileUtilRt.getRelativePath(configPath, file.systemIndependentPath, '/')!!
      if (!file.name.contains('.') && !file.isFile) {
        relativePath += '/'
      }
      if (!onlyPaths.contains(relativePath)) {
        return true
      }
    }

    return onlyExisting && !file.exists()
  }

  if (onlyExisting || onlyPaths != null) {
    val it = result.keySet().iterator()
    while (it.hasNext()) {
      if (isSkipFile(it.next())) {
        it.remove()
      }
    }
  }

  ServiceManagerImpl.processAllImplementationClasses(ApplicationManager.getApplication() as ApplicationImpl, object : PairProcessor<Class<*>, PluginDescriptor> {
    override fun process(aClass: Class<*>, pluginDescriptor: PluginDescriptor?): Boolean {
      val stateAnnotation = StoreUtil.getStateSpec(aClass)
      if (stateAnnotation == null || stateAnnotation.name.isNullOrEmpty() || ExportableComponent::class.java.isAssignableFrom(aClass)) {
        return true
      }

      val storage = sortStoragesByDeprecated(stateAnnotation.storages).firstOrNull() ?: return true
      if (!(storage.roamingType != RoamingType.DISABLED && storage.storageClass == StateStorage::class && storage.scheme == StorageScheme.DEFAULT && !storage.file.isNullOrEmpty())) {
        return true
      }

      var additionalExportFile: File? = null
      var additionalExportPath = stateAnnotation.additionalExportFile
      if (additionalExportPath.isNotEmpty()) {
        // backward compatibility - path can contain macro
        if (additionalExportPath[0] != '$') {
          additionalExportPath = "$ROOT_CONFIG/$additionalExportPath"
        }
        additionalExportFile = File(storageManager.expandMacros(additionalExportPath))
        if (isSkipFile(additionalExportFile)) {
          additionalExportFile = null
        }
      }

      val file = File(storageManager.expandMacros(storage.file))
      val isFileIncluded = !isSkipFile(file)
      if (isFileIncluded || additionalExportFile != null) {
        val files = if (additionalExportFile == null) listOf(file) else if (isFileIncluded) listOf(file, additionalExportFile) else listOf(additionalExportFile)
        val item = ExportableItem(files, if (computePresentableNames) getComponentPresentableName(stateAnnotation, aClass, pluginDescriptor) else "", storage.roamingType)
        result.putValue(file, item)
        if (additionalExportFile != null) {
          result.putValue(additionalExportFile, item)
        }
      }
      return true
    }
  })
  return result
}

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
  val resourceBundleName = (if (pluginDescriptor is IdeaPluginDescriptor && "com.intellij" != pluginDescriptor.pluginId.idString) {
    pluginDescriptor.resourceBundleBaseName
  }
  else {
    OptionsBundle.PATH_TO_BUNDLE
  }) ?: return defaultName

  var classLoader = pluginDescriptor?.pluginClassLoader ?: aClass.classLoader
  if (classLoader != null) {
    val message = messageOrDefault(classLoader, resourceBundleName, defaultName)
    if (message !== defaultName) {
      return message
    }

    if (PlatformUtils.isRubyMine()) {
      // ruby plugin in RubyMine has id "com.intellij", so, we cannot set "resource-bundle" in plugin.xml
      return messageOrDefault(classLoader, "org.jetbrains.plugins.ruby.RBundle", defaultName)
    }
  }
  return defaultName
}

private fun messageOrDefault(classLoader: ClassLoader, bundleName: String, defaultName: String): String {
  val bundle = AbstractBundle.getResourceBundle(bundleName, classLoader) ?: return defaultName
  return CommonBundle.messageOrDefault(bundle, "exportable.$defaultName.presentable.name", defaultName)
}

