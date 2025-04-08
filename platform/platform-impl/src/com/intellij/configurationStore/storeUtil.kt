// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.diagnostic.PluginException
import com.intellij.ide.IdeBundle
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.ide.plugins.PluginUtil
import com.intellij.idea.AppMode
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.CalledInAny
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.nanoseconds

private val LOG: Logger
  get() = Logger.getInstance("#com.intellij.openapi.components.impl.stores.StoreUtil")

/**
 * Only for Java clients; Kotlin clients should use corresponding package-level suspending functions.
 */
@ApiStatus.Obsolete
@Suppress("DEPRECATION")
object StoreUtil {
  /**
   * Don't use this method in tests, instead directly save using state store.
   */
  @JvmOverloads
  @JvmStatic
  @CalledInAny
  fun saveSettings(componentManager: ComponentManager, forceSavingAllSettings: Boolean = false) {
    runInAutoSaveDisabledMode {
      runUnderModalProgressIfIsEdt {
        com.intellij.configurationStore.saveSettings(componentManager, forceSavingAllSettings)
      }
    }
  }

  /**
   * Save all unsaved documents and project settings. Must be called from EDT.
   * Use with care because it blocks EDT. Any new usage should be reviewed.
   */
  @RequiresEdt
  @JvmStatic
  fun saveDocumentsAndProjectSettings(project: Project) {
    runInAutoSaveDisabledMode {
      FileDocumentManager.getInstance().saveAllDocuments()
      runWithModalProgressBlocking(project, IdeBundle.message("progress.saving.project", project.name)) {
        com.intellij.configurationStore.saveSettings(project)
      }
    }
  }

  /**
   * Save all unsaved documents, project and application settings. Must be called from EDT.
   * Use with care because it blocks EDT. Any new usage should be reviewed.
   *
   * @param forceSavingAllSettings if `true` [Storage.useSaveThreshold] attribute will be ignored and settings of all components will be saved
   */
  @RequiresEdt
  @JvmStatic
  @Internal
  fun saveDocumentsAndProjectsAndApp(forceSavingAllSettings: Boolean) {
    runInAutoSaveDisabledMode {
      FileDocumentManager.getInstance().saveAllDocuments()
      runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
        saveProjectsAndApp(forceSavingAllSettings)
      }
    }
  }
}

suspend fun saveSettings(componentManager: ComponentManager, forceSavingAllSettings: Boolean = false): Boolean {
  val storeReloadManager = if (componentManager is Project) StoreReloadManager.getInstance(componentManager) else null
  storeReloadManager?.reloadChangedStorageFiles()
  storeReloadManager?.blockReloadingProjectOnExternalChanges()
  try {
    // Force local ClientId: settings are not saved on disk under a remote ClientId
    withContext(ClientId.localId.asContextElement()) {
      componentManager.stateStore.save(forceSavingAllSettings)
    }
    return true
  }
  catch (e: UnresolvedReadOnlyFilesException) {
    LOG.info(e)
  }
  catch (e: CancellationException) { throw e }
  catch (e: ProcessCanceledException) { throw e }
  catch (e: Throwable) {
    LOG.error("Save settings failed, please restart application", e)
  }
  finally {
    storeReloadManager?.unblockReloadingProjectOnExternalChanges()
  }
  return false
}

fun <T> getStateSpec(persistentStateComponent: PersistentStateComponent<T>): State =
  getStateSpecOrError(persistentStateComponent.javaClass)

fun getStateSpecOrError(componentClass: Class<out PersistentStateComponent<*>>): State =
  getStateSpec(componentClass) ?: throw PluginException.createByClass("No @State annotation found in $componentClass", null, componentClass)

fun getStateSpec(originalClass: Class<*>): State? {
  var aClass = originalClass
  while (true) {
    val stateSpec = aClass.getAnnotation(State::class.java)
    if (stateSpec != null) {
      return stateSpec
    }

    aClass = aClass.superclass ?: break
  }
  return null
}

/**
 * Returns the path to the storage file for the given [PersistentStateComponent].
 * The storage file is defined by [Storage.value] of the [State] annotation, and is located under the APP_CONFIG directory.
 *
 * Returns `null` if there is no [State] or [Storage] annotation on the given class.
 *
 * *NB*: Don't use this method without a strict reason: the storage location is an implementation detail.
 */
@Internal
fun getPersistentStateComponentStorageLocation(clazz: Class<*>): Path? =
  getDefaultStoragePathSpec(clazz)?.let { fileSpec ->
    ApplicationManager.getApplication().getService(IComponentStore::class.java).storageManager.expandMacro(fileSpec)
  }

/**
 * Returns the default storage file specification for the given [PersistentStateComponent] as defined by [Storage.value]
 */
fun getDefaultStoragePathSpec(clazz: Class<*>): String? =
  getStateSpec(clazz)?.let { getDefaultStoragePathSpec(it) }

fun getDefaultStoragePathSpec(state: State): String? {
  val storage = state.storages.find { !it.deprecated }
  return storage?.let { getStoragePathSpec(storage) }
}

private fun getStoragePathSpec(storage: Storage): String {
  @Suppress("DEPRECATION", "removal")
  val pathSpec = storage.value.ifEmpty { storage.file }
  return if (storage.roamingType == RoamingType.PER_OS) getOsDependentStorage(pathSpec) else pathSpec
}

@Internal
fun getOsDependentStorage(storagePathSpec: String): String =
  "${getPerOsSettingsStorageFolderName()}/${storagePathSpec}"

@Internal
fun getPerOsSettingsStorageFolderName(): String = when {
  SystemInfoRt.isMac -> "mac"
  SystemInfoRt.isWindows -> "windows"
  SystemInfoRt.isLinux -> "linux"
  SystemInfoRt.isFreeBSD -> "freebsd"
  else -> if (SystemInfoRt.isUnix) "unix" else "other_os"
}

/**
 * Converts fileSpec passed to [StreamProvider]'s methods to a relative path from the root config directory.
 */
@Internal
fun getFileRelativeToRootConfig(fileSpecPassedToProvider: String): String =
  // For PersistentStateComponents the fileSpec is passed without the 'options' folder, e.g. 'editor.xml' or 'mac/keymaps.xml'
  // OTOH for schemas it is passed together with the containing folder, e.g. 'keymaps/my_keymap.xml'
  if (!fileSpecPassedToProvider.contains("/") || fileSpecPassedToProvider.startsWith(getPerOsSettingsStorageFolderName() + "/")) {
    "${PathManager.OPTIONS_DIRECTORY}/${fileSpecPassedToProvider}"
  }
  else fileSpecPassedToProvider

/**
 * @param forceSavingAllSettings Whether to force save non-roamable component configuration.
 */
suspend fun saveProjectsAndApp(forceSavingAllSettings: Boolean, onlyProject: Project? = null) {
  val start = System.nanoTime()

  coroutineScope {
    val saveAppJob = launch {
      saveSettings(ApplicationManager.getApplication(), forceSavingAllSettings)
    }
    val projects = if (onlyProject != null) sequenceOf(onlyProject) else getOpenedProjects()
    for (project in projects) {
      launch {
        withBackgroundProgress(project, IdeBundle.message("progress.saving.project", project.name)) {
          saveSettings(project, forceSavingAllSettings)
          saveAppJob.join()
        }
      }
    }
  }

  val duration = (System.nanoTime() - start).nanoseconds.inWholeMilliseconds
  if (duration > 1000 || LOG.isDebugEnabled) {
    LOG.info("saveProjectsAndApp took ${duration} ms")
  }
}

inline fun <T> runInAutoSaveDisabledMode(task: () -> T): T {
  SaveAndSyncHandler.getInstance().disableAutoSave().use {
    return task()
  }
}

inline fun runInAllowSaveMode(isSaveAllowed: Boolean = true, task: () -> Unit) {
  val app = ApplicationManagerEx.getApplicationEx()
  if (isSaveAllowed == app.isSaveAllowed) {
    task()
    return
  }

  app.isSaveAllowed = isSaveAllowed
  try {
    task()
  }
  finally {
    app.isSaveAllowed = !isSaveAllowed
  }
}

@RequiresEdt
@Internal
fun forPoorJavaClientOnlySaveProjectIndEdtDoNotUseThisMethod(project: Project, forceSavingAllSettings: Boolean = false) {
  runInAutoSaveDisabledMode {
    runWithModalProgressBlocking(project, IdeBundle.message("progress.saving.project", project.name)) {
      saveSettings(project, forceSavingAllSettings = forceSavingAllSettings)
    }
  }
}

/**
 * This is a temporary workaround for non-reactive settings in RD/CWM.
 * If you modify settings from an action (not from the settings dialog),
 * you can call this method to synchronize changed settings in Remote Development
 * and CodeWithMe.
 */
@Internal
fun saveSettingsForRemoteDevelopment(componentManager: ComponentManager) {
  if (!AppMode.isRemoteDevHost() && !PlatformUtils.isJetBrainsClient())
    return

  currentThreadCoroutineScope().launch {
    // Don't replace with `saveSettings()`, it can't save under a remote clientId
    componentManager.stateStore.save()
  }
}

@Internal
class UnresolvedReadOnlyFilesException(val files: List<VirtualFile>) : RuntimeException()
