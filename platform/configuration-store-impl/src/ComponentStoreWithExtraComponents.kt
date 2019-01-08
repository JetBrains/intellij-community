// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.SettingsSavingComponent
import com.intellij.openapi.components.impl.stores.SaveSessionAndFile
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.containers.ContainerUtil

abstract class ComponentStoreWithExtraComponents : ComponentStoreImpl() {
  private val settingsSavingComponents = ContainerUtil.createLockFreeCopyOnWriteList<SettingsSavingComponent>()

  override fun initComponent(component: Any, isService: Boolean) {
    if (component is SettingsSavingComponent) {
      settingsSavingComponents.add(component)
    }

    super.initComponent(component, isService)
  }

  override fun doSave(errors: MutableList<Throwable>, readonlyFiles: MutableList<SaveSessionAndFile>, isForce: Boolean) {
    for (settingsSavingComponent in settingsSavingComponents) {
      runAndCollectException(errors) {
        settingsSavingComponent.save()
      }
    }

    super.doSave(errors, readonlyFiles, isForce)
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
