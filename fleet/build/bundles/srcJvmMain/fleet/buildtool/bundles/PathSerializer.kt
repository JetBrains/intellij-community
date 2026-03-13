package fleet.buildtool.bundles

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path
import kotlin.io.path.absolutePathString

object PathSerializer : KSerializer<Path> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("java.nio.file.Path")

  override fun deserialize(decoder: Decoder): Path =
    Path.of(decoder.decodeString())

  override fun serialize(encoder: Encoder, value: Path) {
    encoder.encodeString(value.absolutePathString())
  }
}
