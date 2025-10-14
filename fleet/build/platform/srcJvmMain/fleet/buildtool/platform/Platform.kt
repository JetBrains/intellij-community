package fleet.buildtool.platform

import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.io.Serializable as JavaSerializable

val supportedPlatforms = listOf(
  Platform.Windows.WindowsX64,
  Platform.Windows.WindowsAarch64,
  Platform.Linux.LinuxX64,
  Platform.Linux.LinuxAarch64,
  Platform.MacOs.MacOsX64,
  Platform.MacOs.MacOsAarch64,
)
val supportedOses = supportedPlatforms.map { it.os }.toSet()

@Serializable
sealed class Platform(val os: OS) : JavaSerializable {
  abstract val arch: Arch

  @Serializable
  sealed class Windows(override val arch: Arch) : Platform(OS.WINDOWS) {
    @Serializable
    object WindowsX64 : Windows(Arch.X64) {
      private fun readResolve(): Any = WindowsX64
    }

    @Serializable
    object WindowsAarch64 : Windows(Arch.AARCH64) {
      private fun readResolve(): Any = WindowsAarch64
    }
  }

  @Serializable
  sealed class Linux(override val arch: Arch) : Platform(OS.LINUX) {
    @Serializable
    object LinuxX64 : Linux(Arch.X64) {
      private fun readResolve(): Any = LinuxX64
    }

    @Serializable
    object LinuxAarch64 : Linux(Arch.AARCH64) {
      private fun readResolve(): Any = LinuxAarch64
    }
  }

  @Serializable
  sealed class MacOs(override val arch: Arch) : Platform(OS.MACOS) {
    @Serializable
    object MacOsX64 : MacOs(Arch.X64) {
      private fun readResolve(): Any = MacOsX64
    }

    @Serializable
    object MacOsAarch64 : MacOs(Arch.AARCH64) {
      private fun readResolve(): Any = MacOsAarch64
    }
  }

  companion object {
    fun buildPlatform(): Platform {
      val os = OS.currentOs()
      val arch = Arch.currentArch()
      return supportedPlatforms.findPlatform(os, arch)
    }

    fun windows(arch: Arch) = when (arch) {
      Arch.AARCH64 -> Windows.WindowsAarch64
      Arch.X64 -> Windows.WindowsX64
    }

    fun linux(arch: Arch) = when (arch) {
      Arch.AARCH64 -> Linux.LinuxAarch64
      Arch.X64 -> Linux.LinuxX64
    }

    fun macos(arch: Arch) = when (arch) {
      Arch.AARCH64 -> MacOs.MacOsAarch64
      Arch.X64 -> MacOs.MacOsX64
    }

    fun List<Platform>.findPlatform(os: OS, arch: Arch) = singleOrNull { p ->
      p.os == os && p.arch == arch
    } ?: error("unsupported platform $os $arch (or more than one supported platform is matching this triple)")
  }
}

enum class Arch {
  AARCH64, X64;

  companion object {
    fun fromString(arch: String): Arch = when (arch.lowercase()) {
      "x86_64", "amd64", "x64" -> X64
      "arm64", "aarch64" -> AARCH64
      else -> error("unsupported arch '$arch'")
    }

    fun currentArch(): Arch {
      val arch = System.getProperty("os.arch") ?: error("failed to resolve system property 'os.arch'")
      return fromString(arch)
    }
  }
}

enum class OS {
  MACOS, WINDOWS, LINUX;

  companion object {
    fun fromString(os: String): OS = with(os.lowercase()) {
      when {
        contains("win") -> WINDOWS
        contains("mac") -> MACOS
        contains("nix") || contains("nux") -> LINUX
        else -> error("unsuppported OS '$os'")
      }
    }

    fun currentOs(): OS {
      val os = System.getProperty("os.name") ?: error("failed to resolve system property 'os.name'")
      return fromString(os)
    }
  }
}

enum class LibCImplementation {
  GLIBC, MUSL;

  companion object {
    fun fromString(libc: String): LibCImplementation = when (libc.lowercase()) {
      "musl" -> MUSL
      "glibc" -> GLIBC
      else -> error("unsupported libc implementation '$libc'")
    }

    // TODO: proper heuristic on libc detection for alpine linux support for example
    fun currentLibc() = GLIBC
  }
}

/**
 * String representation of OS in Fleet parts (S3 storage, artefact names, etc.)
 */
fun OS.toFleetPartOsString() = when (this) {
  OS.MACOS -> "macos"
  OS.WINDOWS -> "windows"
  OS.LINUX -> "linux"
}

/**
 * String representation of Arch in Fleet parts (S3 storage, artefact names, etc.)
 */
fun Arch.toFleetPartArchString() = when (this) {
  Arch.AARCH64 -> "aarch64"
  Arch.X64 -> "x64"
}

fun Arch.distributionArchSuffix() = when (this) {
  Arch.AARCH64 -> "-aarch64"
  Arch.X64 -> ""
}

/**
 * String representation of lib C implementation in Fleet parts (S3 storage, Gradle tooling, etc.)
 */
fun LibCImplementation.toFleetPartLibcString() = when (this) {
  LibCImplementation.GLIBC -> null // we do not specify GLIBC in the distribution slug, as of legacy
  LibCImplementation.MUSL -> "musl"
}

/**
 * Converts FleetPlatform to the distribution slug used in our S3 for Fleet parts
 *
 * Beware, changing this code might result to a massive breaking change for customers of Fleet.
 */
fun Platform.toS3DistributionSlug(withLibC: LibCImplementation? = null): String {
  val osName = os.toFleetPartOsString()
  val archName = arch.toFleetPartArchString()
  val libc = withLibC?.toFleetPartLibcString()

  return when {
    libc != null -> "${osName}_${libc}-$archName"
    else -> "${osName}_$archName"
  }
}

fun Path.withPlatformBinaryExtension(platform: Platform): Path = when (platform) {
  is Platform.Windows -> resolveSibling("${fileName}.exe")
  else -> this
}

fun Platform.binaryExtension() = when (this) {
  is Platform.Windows -> ".exe"
  else -> ""
}

fun String.withPlatformBinaryExtension(platform: Platform): String = "$this${platform.binaryExtension()}"
