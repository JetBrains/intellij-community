package org.jetbrains.intellij.build.dev

import com.intellij.util.io.Decompressor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.devDistSignatureOf
import java.nio.file.Path

@ApiStatus.Internal
@Serializable
data class DevPluginLibraryResourceConfiguration(
  @JvmField val generatorIndex: Int,
  @JvmField val libraryName: String,
  @JvmField val targetPath: String,
  @JvmField val inputKind: String,
)

@ApiStatus.Internal
fun extractDevPluginLibraryResources(archive: Path, targetDirectory: Path) {
  Decompressor.Zip(archive).extract(targetDirectory)
}

@ApiStatus.Internal
fun devPluginLibraryResourceSignature(configuration: DevPluginLibraryResourceConfiguration, input: DevPluginReference): String {
  require(configuration.generatorIndex >= 0) { "A library resource requires a nonnegative generator index" }
  require(configuration.libraryName.isNotBlank() && configuration.libraryName.none { it == '\u0000' || it == '\r' || it == '\n' }) {
    "A library resource requires a library name"
  }
  validatePreparationPath(configuration.targetPath)
  require(configuration.inputKind in setOf("file", "directory")) { "A library resource requires a file or directory input kind" }
  require(configuration.inputKind != "directory" || input.path.isNotEmpty()) { "A library resource requires an archive path within its directory input" }
  val values = listOf("library-resource-v1", configuration.generatorIndex.toString(), configuration.libraryName, configuration.targetPath,
                      input.artifact, input.path, configuration.inputKind)
  return devDistSignatureOf(values)
}
