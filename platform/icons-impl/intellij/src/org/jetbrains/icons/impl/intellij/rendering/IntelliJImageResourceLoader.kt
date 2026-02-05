// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering

import com.intellij.ui.icons.ImageDataLoader
import com.intellij.ui.icons.ImageDataLoaderDescriptor
import com.intellij.ui.icons.LoadIconParameters
import com.intellij.ui.scale.ScaleContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.icons.impl.rendering.DefaultImageModifiers
import org.jetbrains.icons.modifiers.svgPatcher
import org.jetbrains.icons.patchers.combineWith
import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.ImageResourceLoader
import java.awt.Image
import java.awt.image.ImageFilter

abstract class BaseIntelliJImageResourceLoader: SwingImageResourceLoader {
  protected fun generateLoadIconParameters(imageModifiers: ImageModifiers?): LoadIconParameters {
    val filters = mutableListOf<ImageFilter>()
    val colorFilter = imageModifiers?.colorFilter
    if (colorFilter != null) {
      filters.add(colorFilter.toAwtFilter())
    }
    val knownModifiers = imageModifiers as? DefaultImageModifiers
    val strokePatcher = knownModifiers?.stroke?.let { stroke ->
      svgPatcher {
        replace("fill", "transparent")
        add("stroke", stroke.toHex())
      }
    }
    val colorPatcher = (imageModifiers?.svgPatcher combineWith strokePatcher)?.toIJPatcher()
    return LoadIconParameters(
      filters = filters,
      isDark = knownModifiers?.isDark ?: false,
      colorPatcher = colorPatcher,
      isStroke = knownModifiers?.stroke != null
    )
  }
}

@Serializable(with = IntelliJImageResourceLoaderSerializer::class)
class IntelliJImageResourceLoader(
  val dataLoader: ImageDataLoader
): BaseIntelliJImageResourceLoader() {
  override fun getExpectedDimensions(): Pair<Int, Int> {
    val img = loadImage(ScaleContext.create()) ?: return 0 to 0
    return img.getWidth(null) to img.getHeight(null)
  }

  override fun loadImage(scale: ScaleContext, imageModifiers: ImageModifiers?): Image? {
    return dataLoader.loadImage(
      parameters = generateLoadIconParameters(imageModifiers),
      scaleContext = scale
    )
  }
}

interface SwingImageResourceLoader: ImageResourceLoader {
  fun getExpectedDimensions(): Pair<Int, Int>
  fun loadImage(scale: ScaleContext, imageModifiers: ImageModifiers? = null): Image?
}

object IntelliJImageResourceLoaderSerializer: KSerializer<IntelliJImageResourceLoader> {
  private val actualSerializer = IconLoaderDescriptorHolder.serializer()

  override val descriptor: SerialDescriptor = actualSerializer.descriptor

  override fun serialize(encoder: Encoder, value: IntelliJImageResourceLoader) {
    actualSerializer.serialize(encoder, IconLoaderDescriptorHolder(value.dataLoader.serializeToByteArray()))
  }

  override fun deserialize(decoder: Decoder): IntelliJImageResourceLoader {
    val descriptor = actualSerializer.deserialize(decoder).descriptor
    return IntelliJImageResourceLoader(descriptor?.createIcon() ?: error("Unable to restore data loader from descriptor"))
  }
}

@Serializable
class IconLoaderDescriptorHolder(
  val descriptor: ImageDataLoaderDescriptor?
)