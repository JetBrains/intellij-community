package fleet.buildtool.scrambling

import org.slf4j.Logger
import java.nio.file.Path

interface JarScrambler {

  suspend fun scramble(
    classpath: List<Path>,
    jarsToScramble: List<Path>,
    passthroughJars: List<Path>,
    outputDirectory: Path,
    logger: Logger,
  )
}