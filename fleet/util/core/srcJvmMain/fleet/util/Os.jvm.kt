// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

@Actual
internal fun getNameJvm(): String = System.getProperty("os.name")

@Actual
internal fun getVersionJvm(): String = System.getProperty("os.version").lowercase()

@Actual
internal fun getArchJvm(): String = System.getProperty("os.arch")

@Actual
internal fun getLinuxDistroIdJvm(): String? = cachedLinuxDistroId

// Only `ID` is exposed on purpose: `VERSION_ID` is a build stamp on rolling distros, so reporting it would
// fingerprint rather than version.
private val cachedLinuxDistroId: String? by lazy {
  try {
    val id = readOsReleaseId()
    when {
      // MX ships Debian's os-release verbatim, so /etc/lsb-release is the only thing naming it.
      id == "debian" && isMxLinux() -> "mx"
      id != null -> id
      else -> readKeyValueFile(Path.of("/etc/lsb-release"))?.get("DISTRIB_ID")?.lowercase()?.takeIf { it.isNotEmpty() }
    }
  }
  catch (_: Exception) {
    null
  }
}

// Per the os-release spec /etc/os-release must be used exclusively when it exists, even if it turns out to be
// unreadable or to carry no ID; /usr/lib/os-release is a fallback only for its absence.
private fun readOsReleaseId(): String? =
  (readKeyValueFile(Path.of("/etc/os-release")) ?: readKeyValueFile(Path.of("/usr/lib/os-release")))
    ?.get("ID")
    ?.lowercase()
    ?.takeIf { it.isNotEmpty() }

// Same approach as fastfetch: src/detection/os/os_linux.c, "Hack for MX Linux" (fastfetch-cli/fastfetch#847).
private fun isMxLinux(): Boolean =
  readKeyValueFile(Path.of("/etc/lsb-release"))?.get("DISTRIB_ID").equals("mx", ignoreCase = true)

// null when the file does not exist, an empty map when it exists but cannot be read — the two are not
// interchangeable, see readOsReleaseId
private fun readKeyValueFile(path: Path): Map<String, String>? =
  try {
    Files.readAllLines(path).asSequence()
      .map { it.trim() }
      .filter { !it.startsWith("#") && it.contains('=') }
      .associate { line ->
        line.substringBefore('=').trim() to line.substringAfter('=').trim().trim('"', '\'')
      }
  }
  catch (_: NoSuchFileException) {
    null
  }
  catch (_: IOException) {
    emptyMap()
  }
