// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij

import kotlinx.serialization.modules.SerializersModuleBuilder
import org.jetbrains.icons.DynamicIcon
import org.jetbrains.icons.Icon
import org.jetbrains.icons.design.IconDesigner
import org.jetbrains.icons.legacyIconSupport.SwingIconManager
import org.jetbrains.icons.impl.DefaultIconManager
import org.jetbrains.icons.impl.intellij.custom.CustomIconLayerRegistration
import org.jetbrains.icons.impl.intellij.custom.CustomImageResourceLoader
import org.jetbrains.icons.impl.intellij.custom.CustomLegacyIconSerializer
import org.jetbrains.icons.impl.intellij.design.IntelliJIconDesigner
import org.jetbrains.icons.impl.intellij.rendering.IntelliJIconRendererManager
import org.jetbrains.icons.impl.intellij.rendering.IntelliJImageResourceLoader
import org.jetbrains.icons.impl.intellij.rendering.IntelliJImageResourceLoaderSerializer
import org.jetbrains.icons.impl.intellij.rendering.IntelliJImageResourceProvider
import org.jetbrains.icons.impl.rendering.SwingIcon
import org.jetbrains.icons.rendering.IconRendererManager
import org.jetbrains.icons.rendering.ImageResourceLoader
import org.jetbrains.icons.rendering.ImageResourceProvider

class IntelliJIconManager : DefaultIconManager(), SwingIconManager {
  override fun icon(designer: IconDesigner.() -> Unit): Icon {
    val ijIconDesigner = IntelliJIconDesigner()
    ijIconDesigner.designer()
    return ijIconDesigner.build()
  }

  override fun dynamicIcon(icon: Icon): DynamicIcon {
    return createDynamicIcon(icon) { dynamicIcon, content ->
      // TODO Send updates over network (also listen for them)
    }
  }

  override fun toSwingIcon(icon: Icon): javax.swing.Icon {
    return SwingIcon(icon)
  }

  override fun SerializersModuleBuilder.buildCustomSerializers() {
    CustomLegacyIconSerializer.registerSerializersTo(this)
    CustomImageResourceLoader.registerSerializersTo(this)
    CustomIconLayerRegistration.registerSerializersTo(this)
    polymorphic(ImageResourceLoader::class, IntelliJImageResourceLoader::class, IntelliJImageResourceLoaderSerializer)
  }

  companion object {
    fun activate() {
      org.jetbrains.icons.IconManager.activate(IntelliJIconManager())
      IconRendererManager.activate(IntelliJIconRendererManager())
      ImageResourceProvider.activate(IntelliJImageResourceProvider())
    }
  }
}