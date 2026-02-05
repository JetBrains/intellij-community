// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.design

import com.intellij.ui.icons.findIconLoaderByPath
import org.jetbrains.icons.modifiers.IconModifier
import org.jetbrains.icons.impl.design.DefaultIconDesigner
import org.jetbrains.icons.impl.intellij.rendering.IntelliJImageResourceLoader
import org.jetbrains.icons.layers.IconLayer

class IntelliJIconDesigner: DefaultIconDesigner() {
  override fun image(path: String, classLoader: ClassLoader?, modifier: IconModifier) {
    val loader = findIconLoaderByPath(path, classLoader ?: this.javaClass.classLoader)
    image(IntelliJImageResourceLoader(loader), modifier)
  }

  override fun createNestedDesigner(): DefaultIconDesigner {
    return IntelliJIconDesigner()
  }
}