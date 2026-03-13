package fleet.buildtool.license.rust

import fleet.buildtool.license.JetbrainsLicenceEntry
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.runProcessAndCaptureOutput
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

interface RustLicenseReader {

  /**
   * Generate a licenses JSON file for the given Rust package containing serialized [JetbrainsLicenceEntry].
   *
   * @param cargoManifest the Cargo.toml manifest of the package of which to generate the licenses file
   * @param output the path to the generated licenses file
   *
   * @return the path of the generated licenses file
   */
  suspend fun generateLicensesFile(cargoManifest: Path, output: Path, logger: Logger): Path
}

class CargoAboutLicenseReader(
  val cargoAboutBinary: Path,
  val cargoAboutConfiguration: Path,
) : RustLicenseReader {
  private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
  }

  @OptIn(ExperimentalSerializationApi::class)
  override suspend fun generateLicensesFile(cargoManifest: Path, output: Path, logger: Logger): Path {
    val entries = readCargoAboutLicenses(cargoManifest, logger).licenses.flatMap { license ->
      license.crates.map { crate ->
        JetbrainsLicenceEntry(
          name = crate.name,
          version = crate.version,
          url = crate.repository ?: "https://crates.io/crates/${crate.name}",
          license = license.id,
          licenseUrl = crate.licenseUrl(license.relativeLicensePath),
        )
      }
    }.toSet()

    json.encodeToStream(
      ListSerializer(JetbrainsLicenceEntry.serializer()),
      entries.sortedBy { it.name },
      output.outputStream(),
    )
    return output
  }

  @OptIn(ExperimentalSerializationApi::class)
  private suspend fun readCargoAboutLicenses(cargoManifest: Path, logger: Logger): CargoAboutJsonOutput {
    val workingDir = createTempDirectory("cargo-about-generation")
    val cargoAboutOutput = workingDir.resolve("cargo-about_licenses.json")
    val cmd = listOf(
      cargoAboutBinary.absolutePathString(),
      "generate",
      "--format=json",
      "--manifest-path=${cargoManifest.absolutePathString()}",
      "--config=${cargoAboutConfiguration.absolutePathString()}",
      "--output-file=${cargoAboutOutput.absolutePathString()}",
    )
    logger.info("Generating licences using:\n  ${cmd.joinToString(" ")}")
    val result = runProcessAndCaptureOutput(
      workingDir = workingDir,
      command = cmd,
      outputListener = object : ProcessOutputListener {
        override fun onStdoutLine(line: String, pid: Long) = logger.info("[cargo-about] [$pid] $line")
        override fun onStderrLine(line: String, pid: Long) = logger.error("[cargo-about] [$pid] $line")
      },
    )
    require(result.exitCode == 0) { "license generation failed with exit code ${result.exitCode}" }
    return json.decodeFromStream(
      CargoAboutJsonOutput.serializer(),
      cargoAboutOutput.inputStream(),
    )
  }
}

@Serializable
private data class CargoAboutJsonOutput(
  val licenses: List<CargoAboutLicense>,
)

@Serializable
private data class CargoAboutLicense(
  val id: String,
  val name: String?,
  val text: String?,
  @SerialName(value = "source_path") val sourcePath: String?,
  @SerialName(value = "used_by") val usedBy: List<CargoAboutLicenseUser>,
) {
  val crates
    get() = usedBy.map { it.crate }

  val relativeLicensePath
    get() = sourcePath?.let { Path.of(it) }?.let { path ->
      // if cargo-about is able to harvest crate definition from https://clearlydefined.io/
      // it will use a path relative to the repo root
      // if not, it will scan file system manually and return an absolute path in .cargo/registry
      when {
        path.isAbsolute -> {
          // this license is used by several crates,
          // but the path is absolute and obviously only one of them is a valid prefix
          val definingCrate = crates.single { path.startsWith(Path.of(it.manifestPath).parent) }
          Path.of(definingCrate.manifestPath).parent.relativize(path)
        }

        else -> path
      }
    }
}

@Serializable
private data class CargoAboutLicenseUser(
  val crate: CargoAboutCrate,
  val path: String?,
)

@Serializable
private data class CargoAboutCrate(
  val name: String,
  val version: String,
  val repository: String?,
  @SerialName(value = "manifest_path") val manifestPath: String,
) {
  fun licenseUrl(relativeLicensePath: Path?): String? = relativeLicensePath?.let { path ->
    if (repository != null) {
      "${repository.removeSuffix("/")}/blob/HEAD/$path"
    }
    else {
      "https://docs.rs/crate/${name}/${version}/source/$path"
    }
  }
}
