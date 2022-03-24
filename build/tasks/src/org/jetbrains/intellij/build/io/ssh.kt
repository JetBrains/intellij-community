// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import net.schmizz.sshj.xfer.LocalDestFile
import net.schmizz.sshj.xfer.LocalFileFilter
import net.schmizz.sshj.xfer.LocalSourceFile
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.concurrent.TimeUnit

internal class NioFileDestination(private val file: Path) : LocalDestFile {
  override fun getOutputStream(): OutputStream = Files.newOutputStream(file)

  override fun getChild(name: String?) = throw UnsupportedOperationException()

  override fun getTargetFile(filename: String): LocalDestFile {
    return this
  }

  override fun getTargetDirectory(dirname: String?): LocalDestFile {
    return this
  }

  override fun setPermissions(perms: Int) {
    Files.setPosixFilePermissions(file, fromOctalFileMode(perms))
  }

  override fun setLastAccessedTime(t: Long) {
    // ignore
  }

  override fun setLastModifiedTime(t: Long) {
    // ignore
  }
}

internal class NioFileSource(private val file: Path, private val filePermission: Int = -1) : LocalSourceFile {
  override fun getName() = file.fileName.toString()

  override fun getLength() = Files.size(file)

  override fun getInputStream(): InputStream = Files.newInputStream(file)

  override fun getPermissions(): Int {
    return if (filePermission == -1) toOctalFileMode(Files.getPosixFilePermissions(file)) else filePermission
  }

  override fun isFile() = true

  override fun isDirectory() = false

  override fun getChildren(filter: LocalFileFilter): Iterable<LocalSourceFile> = emptyList()

  override fun providesAtimeMtime() = false

  override fun getLastAccessTime() = System.currentTimeMillis() / 1000

  override fun getLastModifiedTime() = TimeUnit.MILLISECONDS.toSeconds(Files.getLastModifiedTime(file).toMillis())
}

private fun toOctalFileMode(permissions: Set<PosixFilePermission?>): Int {
  var result = 0
  for (permissionBit in permissions) {
    when (permissionBit) {
      PosixFilePermission.OWNER_READ -> result = result or 256
      PosixFilePermission.OWNER_WRITE -> result = result or 128
      PosixFilePermission.OWNER_EXECUTE -> result = result or 64
      PosixFilePermission.GROUP_READ -> result = result or 32
      PosixFilePermission.GROUP_WRITE -> result = result or 16
      PosixFilePermission.GROUP_EXECUTE -> result = result or 8
      PosixFilePermission.OTHERS_READ -> result = result or 4
      PosixFilePermission.OTHERS_WRITE -> result = result or 2
      PosixFilePermission.OTHERS_EXECUTE -> result = result or 1
    }
  }
  return result
}

private val decodeMap = arrayOf(
  PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ,
  PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ,
  PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ
)

private fun fromOctalFileMode(mode: Int): Set<PosixFilePermission> {
  var mask = 1
  val perms = EnumSet.noneOf(PosixFilePermission::class.java)
  for (flag in decodeMap) {
    if (mask and mode != 0) {
      perms.add(flag)
    }
    mask = mask shl 1
  }
  return perms
}