// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.openapi.Disposable
import com.intellij.util.SVGLoader

// icons maybe loaded before app loaded, so, SVGLoaderCache cannot be as a service
internal class IconDbMaintainer : Disposable, SettingsSavingComponent {
  override fun dispose() {
    SVGLoader.getCache()?.close()
  }

  override suspend fun save() {
    SVGLoader.getCache()?.save()
  }
}