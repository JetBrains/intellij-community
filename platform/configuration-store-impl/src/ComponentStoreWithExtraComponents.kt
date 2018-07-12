// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.SettingsSavingComponent
import com.intellij.project.isDirectoryBased

abstract class ComponentStoreWithExtraComponents : ComponentStoreImpl() {
  private val settingsSavingComponents = com.intellij.util.containers.ContainerUtil.createLockFreeCopyOnWriteList<SettingsSavingComponent>()

  override fun initComponent(component: Any, isService: Boolean) {
    if (component is SettingsSavingComponent) {
      settingsSavingComponents.add(component)
    }

    super.initComponent(component, isService)
  }

  override final fun beforeSaveComponents(errors: MutableList<Throwable>) {
    // component state uses scheme manager in an ipr project, so, we must save it before
    val isIprProject = project?.let { !it.isDirectoryBased } ?: false
    if (isIprProject) {
      settingsSavingComponents.firstOrNull { it is SchemeManagerFactoryBase }?.let {
        try {
          it.save()
        }
        catch(e: Throwable) {
          errors.add(e)
        }
      }
    }
  }

  override final fun afterSaveComponents(errors: MutableList<Throwable>) {
    val isIprProject = project?.let { !it.isDirectoryBased } ?: false
    for (settingsSavingComponent in settingsSavingComponents) {
      if (!isIprProject || settingsSavingComponent !is SchemeManagerFactoryBase) {
        try {
          settingsSavingComponent.save()
        }
        catch (e: Throwable) {
          errors.add(e)
        }
      }
    }
  }
}
