// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.concurrency.SynchronizedClearableLazy
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ComponentStoreWithExtraComponents : ComponentStoreImpl() {
  protected abstract val serviceContainer: ComponentManagerEx

  private val asyncSettingsSavingComponents = SynchronizedClearableLazy {
    val result = mutableListOf<SettingsSavingComponent>()
    // filter for class is not used, as we process only created services
    for (instance in serviceContainer.instances()) {
      if (instance is SettingsSavingComponent) {
        result.add(instance)
      }
      else if (instance is @Suppress("DEPRECATION", "removal") com.intellij.openapi.components.SettingsSavingComponent) {
        result.add(object : SettingsSavingComponent {
          override suspend fun save() {
            withContext(Dispatchers.EDT) {
              @Suppress("removal")
              instance.save()
            }
          }
        })
      }
    }
    result
  }

  final override fun initComponentBlocking(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId) {
    if (component is SettingsSavingComponent) {
      asyncSettingsSavingComponents.drop()
    }
    super.initComponentBlocking(component = component, serviceDescriptor = serviceDescriptor, pluginId = pluginId)
  }

  final override suspend fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId) {
    if (component is SettingsSavingComponent) {
      asyncSettingsSavingComponents.drop()
    }
    super.initComponent(component = component, serviceDescriptor = serviceDescriptor, pluginId = pluginId)
  }

  override fun unloadComponent(component: Any) {
    if (component is SettingsSavingComponent) {
      asyncSettingsSavingComponents.drop()
    }
    super.unloadComponent(component)
  }

  override suspend fun doSave(saveResult: SaveResult, forceSavingAllSettings: Boolean) {
    val sessionManager = createSaveSessionProducerManager()
    saveSettingsAndCommitComponents(saveResult, forceSavingAllSettings, sessionManager)
    sessionManager.save(saveResult)
  }

  internal suspend fun saveSettingsAndCommitComponents(
    saveResult: SaveResult,
    forceSavingAllSettings: Boolean,
    sessionManager: SaveSessionProducerManager
  ) {
    coroutineScope {
      for (settingsSavingComponent in asyncSettingsSavingComponents.value) {
        launch {
          try {
            settingsSavingComponent.save()
          }
          catch (e: CancellationException) { throw e }
          catch (e: Throwable) {
            saveResult.addError(e)
          }
        }
      }
    }

    // SchemeManager (asyncSettingsSavingComponent) must be saved before saving components
    // (component state uses scheme manager in an ipr project, so, we must save it before) so, call it sequentially
    commitComponents(forceSavingAllSettings, sessionManager, saveResult)
  }

  final override suspend fun commitComponents(isForce: Boolean, sessionManager: SaveSessionProducerManager, saveResult: SaveResult) {
    // ensure that this task will not interrupt regular saving
    runCatching {
      commitObsoleteComponents(sessionManager, isProjectLevel = false)
    }.getOrLogException(LOG)
    super.commitComponents(isForce, sessionManager, saveResult)
  }

  internal open fun commitObsoleteComponents(session: SaveSessionProducerManager, isProjectLevel: Boolean) {
    val storageManager = storageManager as? StateStorageManagerImpl ?: return
    for (item in ObsoleteStorageBean.EP_NAME.filterableLazySequence()) {
      val bean = item.instance ?: continue
      if (bean.isProjectLevel != isProjectLevel) continue
      val collapsedPath = bean.file ?: continue
      val storage = storageManager.getOrCreateStorage(collapsedPath, roamingType = RoamingType.DISABLED)
      for (componentName in bean.components) {
        session.getProducer(storage)?.setState(component = null, componentName, item.pluginDescriptor.pluginId, state = null)
      }
    }
  }

  final override fun release() {
    asyncSettingsSavingComponents.drop()
    super.release()
  }
}
