package fleet.buildtool.codecache.shadowing

import fleet.buildtool.codecache.ModuleToPack
import fleet.buildtool.codecache.singleOrNullOrThrow
import java.nio.file.Path
import kotlin.io.path.name

data class ShadowedJarResolution(
  val shadowedJar: Path,
  val consumerJar: Path,
  val needsScrambling: Boolean,
)

class ShadowedJarSpec(
  val allowedConsumerModule: String,
  val consumerJarPattern: Regex,
  val shadowedJarPattern: Regex,
  val jpmsModuleName: String,
  val needsScrambling: Boolean,
) {

  fun resolve(module: ModuleToPack): ShadowedJarResolution? {
    val shadowedJar = module.filesToPack.singleOrNullOrThrow { it.name.matches(shadowedJarPattern) } ?: return null
    require(module.name == allowedConsumerModule) {
      "module '${module.name} is not allowed to pack '$shadowedJar'"
    }
    val consumerJar = module.filesToPack.singleOrNull { it.name.matches(consumerJarPattern) }
    require(consumerJar != null) {
      "module '${module.name} shadows '$shadowedJar' so it must want to pack a jar '$consumerJarPattern'"
    }
    return ShadowedJarResolution(
      shadowedJar = shadowedJar,
      consumerJar = consumerJar,
      needsScrambling = needsScrambling,
    )
  }
}
