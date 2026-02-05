// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.serializers

import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.decodeCachedImageIconFromByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.icons.impl.intellij.custom.CustomLegacyIconSerializer

class CustomCachedImageIconSerializer: CustomLegacyIconSerializer<CachedImageIcon>(CachedImageIcon::class) {
  override fun createSerializer(): KSerializer<CachedImageIcon> = CachedImageIconSerializer
}

object CachedImageIconSerializer: KSerializer<CachedImageIcon> {
  private val actualSerializer = SerializedIconDataHolder.serializer()

  override val descriptor: SerialDescriptor = actualSerializer.descriptor

  override fun serialize(encoder: Encoder, value: CachedImageIcon) {
    actualSerializer.serialize(encoder, SerializedIconDataHolder(value.encodeToByteArray()))
  }

  override fun deserialize(decoder: Decoder): CachedImageIcon {
    val byteArray = actualSerializer.deserialize(decoder).data
    return decodeCachedImageIconFromByteArray(byteArray) as? CachedImageIcon ?: error("Unable to restore CachedImageIcon from byte array")
  }
}

@Serializable
class SerializedIconDataHolder(
  val data: ByteArray
)