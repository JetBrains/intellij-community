package fleet.buildtool.codecache.specs

import fleet.buildtool.codecache.ModuleToPack
import fleet.buildtool.codecache.singleOrNullOrThrow
import java.nio.file.Path
import kotlin.io.path.name

data class ShadowedJarResolution(
  val shadowedJar: Path,
  val consumerJar: Path,
)

class ShadowedJarSpec(
  val allowedConsumerModule: String,
  val consumerJarPattern: Regex,
  val shadowedJarPattern: Regex,
  val jpmsModuleName: String,
) {

  fun resolve(module: ModuleToPack): ShadowedJarResolution? {
    val shadowedJar = module.filesToPack.singleOrNullOrThrow { it.name.matches(shadowedJarPattern) } ?: return null
    require(module.name == allowedConsumerModule) {
      "module '${module.name} is not allowed to pack '$shadowedJar'"
    }
    val consumerJar = module.filesToPack.singleOrNull { it.name.matches(consumerJarPattern) }
    require(consumerJar != null) {
      "module '${module.name} shadows '$shadowedJar' so it must want to pack a jar '$consumerJarPattern'. Jars: ${module.filesToPack.joinToString { it.name }}"
    }
    return ShadowedJarResolution(
      shadowedJar = shadowedJar,
      consumerJar = consumerJar,
    )
  }
}
