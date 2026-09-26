package fleet.buildtool.bundles

import fleet.bundles.CoordinatesPlatform
import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
class FleetResource(
  val files: Set<@Serializable(with = PathSerializer::class) Path>,
  val layer: String,
  val platforms: List<CoordinatesPlatform>?,
)

