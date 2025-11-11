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
import java.nio.file.attribute.*
import java.nio.file.attribute.PosixFilePermission.*
import java.nio.file.spi.FileSystemProvider
import kotlin.io.path.name

@ApiStatus.Internal
fun IjentNioPath.getCachedFileAttributesAndWrapToDosAttributesAdapter(): IjentNioPosixFileAttributesWithDosAdapter? {
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

@ApiStatus.Internal
internal fun IjentNioPath.getCachedFileAttributesAndWrapToDosAttributesAdapterIfNeeded(): BasicFileAttributes? {
  if (SystemInfo.isWindows) {
    return getCachedFileAttributesAndWrapToDosAttributesAdapter()
  }
  else {
    return get()
  }
}

@ApiStatus.Internal
fun <A : BasicFileAttributes> FileSystemProvider.readAttributesUsingDosAttributesAdapter(
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

@ApiStatus.Internal
class IjentNioPosixFileAttributeViewWithDosAdapter(
  private val userInfo: EelUserPosixInfo,
  private val posixView: PosixFileAttributeView,
  private val nameStartsWithDot: Boolean,
) : PosixFileAttributeView by posixView, DosFileAttributeView {
  override fun readAttributes(): IjentNioPosixFileAttributesWithDosAdapter =
    IjentNioPosixFileAttributesWithDosAdapter(userInfo, posixView.readAttributes(), nameStartsWithDot)

  override fun setReadOnly(readOnly: Boolean) {
    val permissions: MutableSet<PosixFilePermission> = posixView.readAttributes().permissions()

    // "Read-only" implies "readable", "not read-only" implies "not only readable". In any case, the resource is supposed to be readable.
    permissions += OWNER_READ

    if (readOnly) {
      permissions -= OWNER_WRITE
      permissions -= GROUP_WRITE
      permissions -= OTHERS_WRITE
    }
    else {
      // Windows has a complicated ACL for every file, but this option refers to the old DOS attribute.
      // MS DOS was not a multi-user operating system, but Posix systems are.
      // The dilemma is in how to treat the request to setting the read-only attribute as false:
      // make the file readable for everyone or only for the owner.
      // This code explicitly sets only for the user because in the worst but rare scenario it leads to non-functioning features,
      // but too broad permissions can hypothetically lead to a security issue.
      permissions += OWNER_WRITE
    }

    posixView.setPermissions(permissions)
  }

  override fun setHidden(value: Boolean) {
    // There's no such conception as a hidden file in terms of the Posix filesystem,
    // but there's a tradition to treat all files starting from `.` as hidden.
    //
    // File renaming is certainly something that the caller of `DosFileAttribute.setHidden` does not expect.
    // Throwing an error might have been possible if this implementation was introduced for new code,
    // but it is here to support unknown legacy code.
    //
    // Lastly, it might be worth using extended attributes like `sun.nio.fs.LinuxDosFileAttributeView` does,
    // but it's usually unexpected, it's not supported on macOS, and also it requires efforts for implementing.
    // Reference: https://github.com/JetBrains/JetBrainsRuntime/blob/922b12f30c4cfd6b504d66daf37fb30c7fb1bfe7/src/java.base/linux/classes/sun/nio/fs/LinuxDosFileAttributeView.java
    //
    // Thus, it's better to do nothing in this method.
  }

  override fun setSystem(value: Boolean) {
    // There's no such conception as a "system file" in Posix, though it might be implemented like in `sun.nio.fs.LinuxDosFileAttributeView`
  }

  override fun setArchive(value: Boolean) {
    // There's no such conception as a "system file" in Posix, though it might be implemented like in `sun.nio.fs.LinuxDosFileAttributeView`
  }
}

@ApiStatus.Internal
fun <V : FileAttributeView> FileSystemProvider.getFileAttributeViewUsingDosAttributesAdapter(
  ijentPath: IjentNioPath,
  type: Class<V>,
  vararg options: LinkOption,
): V {
  val actualType = when {
    DosFileAttributeView::class.java.isAssignableFrom(type) -> PosixFileAttributeView::class.java
    else -> type
  }

  val resultAttrs = when (val actualView = getFileAttributeView(ijentPath, actualType, *options)) {
    is PosixFileAttributeView -> IjentNioPosixFileAttributeViewWithDosAdapter(
      userInfo = ijentPath.fileSystem.ijentFs.user as EelUserPosixInfo,
      posixView = actualView,
      nameStartsWithDot = ijentPath.name.startsWith("."),
    )
    else -> actualView
  }

  return type.cast(resultAttrs)
}