// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.openapi.Disposable
import com.intellij.util.SVGLoader

// icons maybe loaded before app loaded, so, SVGLoaderCache cannot be as a service
private class IconDbMaintainer : Disposable, SettingsSavingComponent {
  override fun dispose() {
    SVGLoader.persistentCache?.close()
  }

  override suspend fun save() {
    SVGLoader.persistentCache?.save()
  }
}