// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent.nio

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.CaseSensitivityAttribute
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.platform.eel.EelUserPosixInfo
import com.intellij.platform.ijent.community.impl.nio.EelPosixGroupPrincipal
import com.intellij.platform.ijent.community.impl.nio.EelPosixUserPrincipal
import com.intellij.platform.ijent.community.impl.nio.IjentNioPath
import com.intellij.platform.ijent.community.impl.nio.IjentNioPosixFileAttributes
import org.jetbrains.annotations.ApiStatus
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission.*
import java.nio.file.spi.FileSystemProvider
import kotlin.io.path.name

internal fun IjentNioPath.getCachedFileAttributesAndWrapToDosAttributesAdapter(): IjentNioPosixFileAttributesWithDosAdapter? {
  val cachedAttrs = get() as IjentNioPosixFileAttributes?

  return if (cachedAttrs != null) {
    IjentNioPosixFileAttributesWithDosAdapter(
      userInfo = fileSystem.ijentFs.user as EelUserPosixInfo,
      fileInfo = cachedAttrs,
      nameStartsWithDot = fileName.startsWith("."),
    )
  }
  else {
    cachedAttrs
  }
}

internal fun IjentNioPath.getCachedFileAttributesAndWrapToDosAttributesAdapterIfNeeded(): BasicFileAttributes? {
  if (SystemInfo.isWindows) {
    return getCachedFileAttributesAndWrapToDosAttributesAdapter()
  }
  else {
    return get()
  }
}

internal fun <A : BasicFileAttributes> FileSystemProvider.readAttributesUsingDosAttributesAdapter(
  path: Path,
  ijentPath: IjentNioPath,
  type: Class<A>,
  vararg options: LinkOption,
): A {
  // There's some contract violation at least in com.intellij.openapi.util.io.FileAttributes.fromNio:
  // the function always assumes that the returned object is DosFileAttributes on Windows,
  // and that's always true with the default WindowsFileSystemProvider.

  val actualType = when {
    DosFileAttributes::class.java.isAssignableFrom(type) -> PosixFileAttributes::class.java
    else -> type
  }

  val resultAttrs = when (val actualAttrs = readAttributes(ijentPath, actualType, *options)) {
    is DosFileAttributes -> actualAttrs  // TODO How can it be possible? It's certainly known that the remote OS is GNU/Linux.
    is PosixFileAttributes -> IjentNioPosixFileAttributesWithDosAdapter(
      ijentPath.fileSystem.ijentFs.user as EelUserPosixInfo,
      actualAttrs, path.name.startsWith("."),
    )
    else -> actualAttrs
  }

  return type.cast(resultAttrs)
}

@ApiStatus.Internal
class IjentNioPosixFileAttributesWithDosAdapter(
  private val userInfo: EelUserPosixInfo,
  private val fileInfo: PosixFileAttributes,
  private val nameStartsWithDot: Boolean,
) : CaseSensitivityAttribute, PosixFileAttributes by fileInfo, DosFileAttributes {
  /**
   * Returns `false` if the corresponding file or directory can be modified.
   * Note that returning `true` does not mean that the corresponding file can be read or the directory can be listed.
   */
  override fun isReadOnly(): Boolean = fileInfo.run {
    val owner = owner()
    val group = group()
    return when {
      userInfo.uid == 0 && owner is EelPosixUserPrincipal && owner.uid != 0 ->
        // on unix, root can read everything except the files that they forbid for themselves
        isDirectory

      owner is EelPosixUserPrincipal && owner.uid == userInfo.uid ->
        OWNER_WRITE !in permissions() || (isDirectory && OWNER_EXECUTE !in permissions())

      group is EelPosixGroupPrincipal && group.gid == userInfo.gid ->
        GROUP_WRITE !in permissions() || (isDirectory && GROUP_EXECUTE !in permissions())

      else ->
        OTHERS_WRITE !in permissions() || (isDirectory && OTHERS_EXECUTE !in permissions())
    }
  }

  override fun isHidden(): Boolean = nameStartsWithDot

  override fun isArchive(): Boolean = false

  override fun isSystem(): Boolean = false

  override fun getCaseSensitivity(): FileAttributes.CaseSensitivity {
    if (fileInfo is CaseSensitivityAttribute) return fileInfo.caseSensitivity else return FileAttributes.CaseSensitivity.UNKNOWN
  }
}