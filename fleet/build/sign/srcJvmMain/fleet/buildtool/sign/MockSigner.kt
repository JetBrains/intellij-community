package fleet.buildtool.sign

import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.pathString
import java.io.Serializable as JavaSerializable

object MockSigner : FleetSigner, JavaSerializable {
  override fun signFiles(files: Collection<Path>, options: Map<String, String>, logger: Logger) {
    files.forEach { file ->
      Files.writeString(file.resolveSibling("${file.fileName.pathString}.asc"), "", StandardOpenOption.CREATE)
      logger.info("[mockSign]: pretended to sign '${file.fileName.pathString}'")
    }
  }

  override fun gpgSign(data: Map<String, ByteArray>, options: Map<String, String>, temporaryDirectory: Path, logger: Logger): Map<String, ByteArray> =
    data.mapValues { (key, _) ->
      logger.info("[mockSign]: pretended to gpg sign '$key'")
      byteArrayOf()
    }
}
