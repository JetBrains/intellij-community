// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.design

import org.jetbrains.icons.modifiers.IconModifier
import org.jetbrains.icons.impl.design.DefaultIconDesigner
import org.jetbrains.icons.impl.intellij.ModuleImageResourceLocation

class IntelliJIconDesigner: DefaultIconDesigner() {
  override fun image(path: String, classLoader: ClassLoader?, modifier: IconModifier) {
    if (classLoader == null) error("Specifying classloader for icon image is required in IntelliJ.")
    image(ModuleImageResourceLocation.fromClassLoader(path, classLoader), modifier)
  }

  override fun createNestedDesigner(): DefaultIconDesigner {
    return IntelliJIconDesigner()
  }
}