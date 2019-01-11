// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.async.coroutineDispatchingContext
import com.intellij.openapi.components.SettingsSavingComponent
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class ComponentStoreWithExtraComponents : ComponentStoreImpl() {
  @Suppress("DEPRECATION")
  private val settingsSavingComponents = ContainerUtil.createLockFreeCopyOnWriteList<SettingsSavingComponent>()
  private val asyncSettingsSavingComponents = ContainerUtil.createLockFreeCopyOnWriteList<com.intellij.configurationStore.SettingsSavingComponent>()

  override fun initComponent(component: Any, isService: Boolean) {
    @Suppress("DEPRECATION")
    if (component is com.intellij.configurationStore.SettingsSavingComponent) {
      asyncSettingsSavingComponents.add(component)
    }
    else if (component is SettingsSavingComponent) {
      settingsSavingComponents.add(component)
    }

    super.initComponent(component, isService)
  }

  internal suspend fun saveSettingsSavingComponentsAndCommitComponents(errors: MutableList<Throwable>, isForceSavingAllSettings: Boolean): SaveSessionProducerManager {
    coroutineScope {
      // expects EDT
      launch(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
        for (settingsSavingComponent in settingsSavingComponents) {
          runAndCollectException(errors) {
            settingsSavingComponent.save()
          }
        }
      }

      launch {
        for (settingsSavingComponent in asyncSettingsSavingComponents) {
          runAndCollectException(errors) {
            settingsSavingComponent.save()
          }
        }
      }
    }

    // SchemeManager (old settingsSavingComponent) must be saved before saving components (component state uses scheme manager in an ipr project, so, we must save it before)
    // so, call sequentially it, not inside coroutineScope
    return withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
      createSaveSessionManagerAndSaveComponents(isForceSavingAllSettings, errors)
    }
  }
}

private inline fun <T> runAndCollectException(errors: MutableList<Throwable>, runnable: () -> T): T? {
  try {
    return runnable()
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: Throwable) {
    errors.add(e)
    return null
  }
}
