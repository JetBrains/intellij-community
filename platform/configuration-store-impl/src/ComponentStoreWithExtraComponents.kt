// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// A way to remove obsolete component data.
internal val OBSOLETE_STORAGE_EP = ExtensionPointName<ObsoleteStorageBean>("com.intellij.obsoleteStorage")

abstract class ComponentStoreWithExtraComponents : ComponentStoreImpl() {
  @Suppress("DEPRECATION")
  private val settingsSavingComponents = ContainerUtil.createLockFreeCopyOnWriteList<com.intellij.openapi.components.SettingsSavingComponent>()

  protected abstract val serviceContainer: ComponentManagerImpl

  private val asyncSettingsSavingComponents = SynchronizedClearableLazy {
    val result = mutableListOf<SettingsSavingComponent>()
    serviceContainer.processComponentsAndServices(createIfNeeded = false) {
      if (it is SettingsSavingComponent) {
        result.add(it)
      }
    }
    result
  }

  final override fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId?) {
    if (component is @Suppress("DEPRECATION") com.intellij.openapi.components.SettingsSavingComponent) {
      settingsSavingComponents.add(component)
    }
    else if (component is SettingsSavingComponent) {
      asyncSettingsSavingComponents.drop()
    }

    super.initComponent(component, serviceDescriptor, pluginId)
  }

  override fun unloadComponent(component: Any) {
    if (component is SettingsSavingComponent) {
      asyncSettingsSavingComponents.drop()
    }
    super.unloadComponent(component)
  }

  internal suspend fun saveSettingsSavingComponentsAndCommitComponents(result: SaveResult,
                                                                       forceSavingAllSettings: Boolean,
                                                                       saveSessionProducerManager: SaveSessionProducerManager) {
    coroutineScope {
      // expects EDT
      launch(Dispatchers.EDT) {
        for (settingsSavingComponent in settingsSavingComponents) {
          runAndCollectException(result) {
            settingsSavingComponent.save()
          }
        }
      }

      launch {
        for (settingsSavingComponent in asyncSettingsSavingComponents.value) {
          runAndCollectException(result) {
            settingsSavingComponent.save()
          }
        }
      }
    }

    // SchemeManager (asyncSettingsSavingComponent) must be saved before saving components (component state uses scheme manager in an ipr project, so, we must save it before)
    // so, call it sequentially, not inside coroutineScope
    commitComponentsOnEdt(result, forceSavingAllSettings, saveSessionProducerManager)
  }

  override fun commitComponents(isForce: Boolean, session: SaveSessionProducerManager, saveResult: SaveResult) {
    // ensure that this task will not interrupt regular saving
    runCatching {
      commitObsoleteComponents(session = session, isProjectLevel = false)
    }.getOrLogException(LOG)

    super.commitComponents(isForce, session, saveResult)
  }

  internal open fun commitObsoleteComponents(session: SaveSessionProducerManager, isProjectLevel: Boolean) {
    for (bean in OBSOLETE_STORAGE_EP.lazySequence()) {
      if (bean.isProjectLevel != isProjectLevel) {
        continue
      }

      val storage = (storageManager as? StateStorageManagerImpl)?.getOrCreateStorage(bean.file ?: continue, RoamingType.DISABLED)
      if (storage != null) {
        for (componentName in bean.components) {
          session.getProducer(storage)?.setState(null, componentName, null)
        }
      }
    }
  }
}

private inline fun runAndCollectException(result: SaveResult, runnable: () -> Unit) {
  try {
    runnable()
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    result.addError(e)
  }
}
