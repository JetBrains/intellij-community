package fleet.buildtool.codecache.specs

import java.nio.file.Path
import kotlin.io.path.name

data class ScrambledJarSpec(
  val jarToScramblePattern: Regex,
) {

  fun needsScrambling(jar: Path): Boolean = jarToScramblePattern.matches(jar.name)
}
