// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import org.jetbrains.icons.DynamicIcon
import org.jetbrains.icons.Icon
import org.jetbrains.icons.IconManager
import org.jetbrains.icons.impl.layers.AnimatedIconLayer
import org.jetbrains.icons.impl.layers.IconIconLayer
import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.impl.layers.ImageIconLayer
import org.jetbrains.icons.impl.layers.LayoutIconLayer
import org.jetbrains.icons.impl.layers.ShapeIconLayer
import org.jetbrains.icons.modifiers.IconModifier

abstract class DefaultIconManager: IconManager {
  override fun dynamicIcon(icon: Icon): DynamicIcon {
    return DefaultDynamicIcon("dynamic_" + dynamicIconNextId++, icon as DefaultLayeredIcon)
  }

  protected fun createDynamicIcon(icon: Icon, updateListener: (DynamicIcon, Icon) -> Unit): DynamicIcon {
    return DefaultDynamicIcon("dynamic?" + dynamicIconNextId++, icon as DefaultLayeredIcon, updateListener)
  }

  protected open fun SerializersModuleBuilder.buildCustomSerializers() {
    // Register nothing in default implementation
  }

  override fun getSerializersModule(): SerializersModule {
    return SerializersModule {
      polymorphic(Icon::class, DefaultLayeredIcon::class, DefaultLayeredIcon.serializer())
      polymorphic(Icon::class, DefaultDynamicIcon::class, DefaultDynamicIcon.serializer())
      polymorphic(DynamicIcon::class, DefaultDynamicIcon::class, DefaultDynamicIcon.serializer())
      polymorphic(
        IconModifier::class,
        IconModifier.Companion::class,
        IconModifierConstSerializer
      )

      iconLayer(AnimatedIconLayer::class)
      iconLayer(IconIconLayer::class)
      iconLayer(ImageIconLayer::class)
      iconLayer(LayoutIconLayer::class)
      iconLayer(ShapeIconLayer::class)

      buildCustomSerializers()
    }
  }

  private var dynamicIconNextId = 0
}

private object IconModifierConstSerializer : KSerializer<IconModifier.Companion> {
  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("IconModifier.Companion") {
      element("isEmpty", serialDescriptor<Boolean>())
    }

  override fun serialize(encoder: Encoder, value: IconModifier.Companion) {
    encoder.encodeStructure(descriptor) {
      encodeBooleanElement(descriptor, 0, true)
    }
  }

  override fun deserialize(decoder: Decoder): IconModifier.Companion {
    var v: Boolean? = null
    decoder.decodeStructure(descriptor) {
      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          CompositeDecoder.DECODE_DONE -> break
          0 -> v = decodeBooleanElement(descriptor, 0)
          else -> error("Unexpected element index: $index")
        }
      }
    }
    require(v == true) { "Unexpected value: '$v'" }
    return IconModifier
  }
}