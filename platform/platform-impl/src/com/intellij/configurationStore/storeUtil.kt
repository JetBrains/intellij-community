// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.diagnostic.PluginException
import com.intellij.ide.IdeBundle
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.processOpenedProjects
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt

private val LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.StoreUtil")

/**
 * Only for Java clients.
 * Kotlin clients should use corresponding package-level suspending functions.
 */
class StoreUtil private constructor() {
  companion object {
    /**
     * Do not use this method in tests, instead directly save using state store.
     */
    @JvmOverloads
    @JvmStatic
    @CalledInAny
    fun saveSettings(componentManager: ComponentManager, forceSavingAllSettings: Boolean = false) {
      if (componentManager is Application) {
        SaveAndSyncHandler.getInstance().cancelScheduledSave()
      }
      runBlocking {
        com.intellij.configurationStore.saveSettings(componentManager, forceSavingAllSettings)
      }
    }

    /**
     * Save all unsaved documents and project settings. Must be called from EDT.
     * Use with care because it blocks EDT. Any new usage should be reviewed.
     */
    @CalledInAwt
    @JvmStatic
    fun saveDocumentsAndProjectSettings(project: Project) {
      FileDocumentManager.getInstance().saveAllDocuments()
      saveSettings(project)
    }

    /**
     * Save all unsaved documents, project and application settings. Must be called from EDT.
     * Use with care because it blocks EDT. Any new usage should be reviewed.
     *
     * @param forceSavingAllSettings Whether to force save non-roamable component configuration.
     */
    @CalledInAwt
    @JvmStatic
    fun saveDocumentsAndProjectsAndApp(forceSavingAllSettings: Boolean) {
      SaveAndSyncHandler.getInstance().cancelScheduledSave()

      FileDocumentManager.getInstance().saveAllDocuments()
      runBlocking {
        saveProjectsAndApp(forceSavingAllSettings)
      }
    }
  }
}

@CalledInAny
suspend fun saveSettings(componentManager: ComponentManager, forceSavingAllSettings: Boolean = false): Boolean {
  if (ApplicationManager.getApplication().isDispatchThread) {
    (TransactionGuardImpl.getInstance() as TransactionGuardImpl).assertWriteActionAllowed()
  }

  try {
    componentManager.stateStore.save(forceSavingAllSettings = forceSavingAllSettings)
    return true
  }
  catch (e: UnresolvedReadOnlyFilesException) {
    LOG.info(e)
  }
  catch (e: Throwable) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      LOG.error("Save settings failed", e)
    }
    else {
      LOG.warn("Save settings failed", e)
    }

    val messagePostfix = IdeBundle.message("notification.content.please.restart.0", ApplicationNamesInfo.getInstance().fullProductName,
                                           (if (ApplicationManager.getApplication().isInternal) "<p>" + StringUtil.getThrowableText(e) + "</p>" else ""))

    val pluginId = PluginUtil.getInstance().findPluginId(e)
    val groupId = NotificationGroup.createIdWithTitle("Settings Error", IdeBundle.message("notification.group.settings.error"))
    val notification = if (pluginId == null) {
      Notification(groupId, IdeBundle.message("notification.title.unable.to.save.settings"),
                   IdeBundle.message("notification.content.failed.to.save.settings", messagePostfix),
                   NotificationType.ERROR)
    }
    else {
      PluginManagerCore.disablePlugin(pluginId)
      Notification(groupId, IdeBundle.message("notification.title.unable.to.save.plugin.settings"),
                   IdeBundle.message("notification.content.plugin.failed.to.save.settings.and.has.been.disabled", pluginId.idString, messagePostfix),
                   NotificationType.ERROR)
    }
    notification.notify(componentManager as? Project)
  }
  return false
}

fun <T> getStateSpec(persistentStateComponent: PersistentStateComponent<T>): State {
  return getStateSpecOrError(persistentStateComponent.javaClass)
}

fun getStateSpecOrError(componentClass: Class<out PersistentStateComponent<*>>): State {
  return getStateSpec(componentClass)
         ?: throw PluginException.createByClass("No @State annotation found in $componentClass", null, componentClass)
}

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
 * @param forceSavingAllSettings Whether to force save non-roamable component configuration.
 */
@CalledInAny
suspend fun saveProjectsAndApp(forceSavingAllSettings: Boolean, onlyProject: Project? = null) {
  StoreReloadManager.getInstance().reloadChangedStorageFiles()

  val start = System.currentTimeMillis()
  saveSettings(ApplicationManager.getApplication(), forceSavingAllSettings)
  if (onlyProject == null) {
    saveAllProjects(forceSavingAllSettings)
  }
  else {
    saveSettings(onlyProject, forceSavingAllSettings = true)
  }

  val duration = System.currentTimeMillis() - start
  if (duration > 1000 || LOG.isDebugEnabled) {
    LOG.info("saveProjectsAndApp took $duration ms")
  }
}

@CalledInAny
private suspend fun saveAllProjects(forceSavingAllSettings: Boolean) {
  processOpenedProjects { project ->
    saveSettings(project, forceSavingAllSettings)
  }
}

inline fun <T> runInAutoSaveDisabledMode(task: () -> T): T {
  SaveAndSyncHandler.getInstance().disableAutoSave().use {
    return task()
  }
}