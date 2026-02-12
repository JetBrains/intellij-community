// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.legacyIconSupport

import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.Icon
import java.util.ServiceLoader

@OptIn(ExperimentalIconsApi::class)
interface SwingIconManager {
  fun toSwingIcon(icon: Icon): javax.swing.Icon

  companion object {
    @Volatile
    private var instance: SwingIconManager? = null

    @JvmStatic
    fun getInstance(): SwingIconManager = instance ?: loadFromSPI()

    private fun loadFromSPI(): SwingIconManager =
      ServiceLoader.load(SwingIconManager::class.java).firstOrNull()
      ?: error("IconManager instance is not set and there is no SPI service on classpath.")


    fun activate(manager: SwingIconManager) {
      instance = manager
    }
  }
}