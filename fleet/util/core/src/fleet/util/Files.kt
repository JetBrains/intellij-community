// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

fun formatFileSize(fileSize: Long): String {
  if (fileSize == 0L) return "0 B"
  val rank = ((log10(fileSize.toDouble()) + 0.0000021714778384307465) / 3).toInt()
  val value = fileSize / 1000.0.pow(rank.toDouble())
  val units = arrayOf("B", "kB", "MB", "GB", "TB", "PB", "EB")
  return DecimalFormat("0.##").format(value) + units[rank]
}

fun setExecutable(path: Path) {
  val view = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
  if (view != null) {
    val permissions = view.readAttributes().permissions()
    if (permissions.add(PosixFilePermission.OWNER_EXECUTE)) {
      view.setPermissions(permissions)
    }
  }
}

fun isExecutable(path: Path) = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
  ?.readAttributes()
  ?.permissions()
  ?.contains(PosixFilePermission.OWNER_EXECUTE) == true

/**
 * On DOS-like file systems, sets the RO attribute to the corresponding value.
 * On POSIX file systems, deletes all write permissions when `value` is `true` or
 * adds the "owner-write" one otherwise.
 */
fun setReadOnly(path: Path, value: Boolean) {
  Files.getFileAttributeView(path, PosixFileAttributeView::class.java)?.let { posixView ->
    val permissions = posixView.readAttributes().permissions()
    val modified = if (value) {
      permissions.removeAll(setOf(PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE))
    }
    else {
      permissions.add(PosixFilePermission.OWNER_WRITE)
    }
    if (modified) {
      posixView.setPermissions(permissions)
    }
  }
  ?: Files.getFileAttributeView(path, DosFileAttributeView::class.java)?.setReadOnly(value)
  ?: throw IOException("Not supported: " + path.fileSystem)
}

