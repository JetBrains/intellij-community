// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.design

import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.impl.design.DefaultIconDesigner
import com.intellij.platform.icons.impl.intellij.ModuleImageResourceLocation
import com.intellij.platform.icons.impl.layers.SwingIconLayer

class IntelliJIconDesigner: DefaultIconDesigner() {
  override fun image(path: String, classLoader: ClassLoader?, modifier: IconModifier) {
    if (classLoader == null) error("Specifying classloader for icon image is required in IntelliJ.")
    image(ModuleImageResourceLocation.fromClassLoader(path, classLoader), modifier)
  }

  fun addSwingLayer(icon: javax.swing.Icon, modifier: IconModifier) {
    layers.add(SwingIconLayer(icon, modifier))
  }

  override fun createNestedDesigner(): DefaultIconDesigner {
    return IntelliJIconDesigner()
  }
}