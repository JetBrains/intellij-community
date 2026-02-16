// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij

import kotlinx.serialization.modules.SerializersModuleBuilder
import org.jetbrains.icons.Icon
import org.jetbrains.icons.ImageResourceLocation
import org.jetbrains.icons.design.IconDesigner
import org.jetbrains.icons.impl.DefaultIconManager
import org.jetbrains.icons.impl.intellij.custom.CustomIconLayerRegistration
import org.jetbrains.icons.impl.intellij.custom.CustomLegacyIconSerializer
import org.jetbrains.icons.impl.intellij.design.IntelliJIconDesigner

class IntelliJIconManager : DefaultIconManager() {
  override fun icon(designer: IconDesigner.() -> Unit): Icon {
    val ijIconDesigner = IntelliJIconDesigner()
    ijIconDesigner.designer()
    return ijIconDesigner.build()
  }

  override fun SerializersModuleBuilder.buildCustomSerializers() {
    CustomLegacyIconSerializer.registerSerializersTo(this)
    CustomIconLayerRegistration.registerSerializersTo(this)
    polymorphic(
      ImageResourceLocation::class,
      ModuleImageResourceLocation::class,
      ModuleImageResourceLocation.serializer()
    )
  }
}