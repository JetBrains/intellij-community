package fleet.buildtool.sign

import org.slf4j.Logger
import java.nio.file.Path

val jetSignJsonContentType: Pair<String, String> = "contentType" to "text/plain"

interface FleetSigner {
  fun signFiles(files: Collection<Path>, options: Map<String, String> = emptyMap(), logger: Logger)
  fun gpgSign(data: Map<String, ByteArray>, options: Map<String, String> = emptyMap(), temporaryDirectory: Path, logger: Logger): Map<String, ByteArray>
}
