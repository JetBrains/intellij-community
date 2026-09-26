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
internal fun getLinuxDistroIdJvm(): LinuxDistroId? = cachedLinuxDistroId

// Only `ID` and `ID_LIKE` are exposed on purpose: `VERSION_ID` is a build stamp on rolling distros, so reporting it would
// fingerprint rather than version.
private val cachedLinuxDistroId: LinuxDistroId? by lazy {
  try {
    val ids = readOsReleaseId()
    when {
      // MX ships Debian's os-release verbatim, so /etc/lsb-release is the only thing naming it.
      ids?.id == "debian" && isMxLinux() -> LinuxDistroId("mx", "debian")
      ids != null -> ids
      else -> readKeyValueFile(Path.of("/etc/lsb-release"))?.get("DISTRIB_ID")?.lowercase()?.takeIf { it.isNotEmpty() }?.let { LinuxDistroId(it, null) }
    }
  }
  catch (_: Exception) {
    null
  }
}

// Per the os-release spec /etc/os-release must be used exclusively when it exists, even if it turns out to be
// unreadable or to carry no ID; /usr/lib/os-release is a fallback only for its absence.
private fun readOsReleaseId(): LinuxDistroId? {
  val parsed = (readKeyValueFile(Path.of("/etc/os-release")) ?: readKeyValueFile(Path.of("/usr/lib/os-release"))) ?: return null
  val id = parsed["ID"]?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
  val idLike = parsed["ID_LIKE"]?.lowercase()?.takeIf { it.isNotEmpty() }
  return LinuxDistroId(id, idLike)
}

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
