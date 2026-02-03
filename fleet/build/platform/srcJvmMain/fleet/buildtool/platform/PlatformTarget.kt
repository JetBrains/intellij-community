package fleet.buildtool.platform

import java.io.Serializable

data class PlatformTarget(val platform: Platform, val libCImplementation: LibCImplementation? = null) : Serializable {

  override fun toString(): String = platform.toString() + libCImplementation?.let { "-$it" }.orEmpty()
}

fun PlatformTarget.toRustTargetString(): String = when (platform) {
  Platform.Linux.LinuxAarch64 -> when (libCImplementation) {
    LibCImplementation.GLIBC -> "aarch64-unknown-linux-gnu"
    LibCImplementation.MUSL, null -> "aarch64-unknown-linux-musl"
  }

  Platform.Linux.LinuxX64 -> when (libCImplementation) {
    LibCImplementation.GLIBC -> "x86_64-unknown-linux-gnu"
    LibCImplementation.MUSL, null -> "x86_64-unknown-linux-musl"
  }

  Platform.MacOs.MacOsAarch64 -> "aarch64-apple-darwin"
  Platform.MacOs.MacOsX64 -> "x86_64-apple-darwin"
  Platform.Windows.WindowsAarch64 -> "aarch64-pc-windows-msvc"
  Platform.Windows.WindowsX64 -> "x86_64-pc-windows-msvc"
}

fun Platform.toPlatformTarget(overrideLibCImplementation: LibCImplementation? = null): PlatformTarget =
  PlatformTarget(this, overrideLibCImplementation)

fun PlatformTarget.toS3DistributionSlug(): String = platform.toS3DistributionSlug(libCImplementation)
