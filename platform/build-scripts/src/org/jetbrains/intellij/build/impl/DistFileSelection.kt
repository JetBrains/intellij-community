package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.DistFile
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.OsFamily

internal fun selectDistFiles(
  files: Collection<DistFile>,
  os: OsFamily?,
  arch: JvmArchitecture?,
  libcImpl: LibcImpl?,
): List<DistFile> {
  val result = files.filterTo(mutableListOf()) {
    (os == null && arch == null && libcImpl == null) ||
    (os == null || it.os == null || it.os == os) &&
    (arch == null || it.arch == null || it.arch == arch) &&
    (libcImpl == null || it.libcImpl == null || it.libcImpl == libcImpl)
  }
  result.sortWith(compareBy({ it.relativePath }, { it.os }, { it.arch }))
  return result
}
