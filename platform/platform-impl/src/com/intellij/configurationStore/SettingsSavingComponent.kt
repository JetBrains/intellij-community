// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

interface SettingsSavingComponent {
  suspend fun save()
}

interface SettingsSavingComponentJavaAdapter : SettingsSavingComponent {
  @JvmDefault
  override  suspend fun save() {
    doSave()
  }

  fun doSave()
}