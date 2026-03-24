package fleet.buildtool.codecache.specs

import fleet.buildtool.codecache.singleOrNullOrThrow
import fleet.buildtool.platform.Platform
import java.nio.file.Path

sealed class NativeLibraryExtractor {
  /**
   * Jars would be left untouched.
   */
  object Noop : NativeLibraryExtractor()

  /**
   * Native libraries inside jars will be extracted to [directory]
   */
  data class ExtractTo(
    val directory: Path,
    val specifications: List<NativeLibrariesExtractorSpec>,
  ) : NativeLibraryExtractor() {

    fun specificationFor(jarName: String): NativeLibrariesExtractorSpec? =
      specifications.singleOrNullOrThrow { it.jarNamePattern.containsMatchIn(jarName) }
  }
}

interface NativeLibrariesExtractorSpec {
  val jarNamePattern: Regex
  val allowedExtensions: Set<String>
  val nativeLibrariesSelector: (Path) -> List<Path>
  val platformDetector: PlatformDetector

  fun interface PlatformDetector {
    fun detect(libraryPath: Path, jarPath: Path): Platform?
  }
}

