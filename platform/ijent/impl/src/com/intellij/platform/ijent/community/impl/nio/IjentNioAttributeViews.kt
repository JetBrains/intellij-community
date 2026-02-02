// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.ijent.fs.IjentFileSystemApi
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.UserPrincipal

internal open class IjentNioBasicFileAttributeView(val api: IjentFileSystemApi, val path: EelPath, val nioPath: IjentNioPath) : BasicFileAttributeView {
  override fun name(): String? {
    return "basic"
  }

  override fun readAttributes(): BasicFileAttributes {
    return Files.readAttributes(nioPath, BasicFileAttributes::class.java)
  }

  override fun setTimes(lastModifiedTime: FileTime?, lastAccessTime: FileTime?, createTime: FileTime?) {
    val builder = EelFileSystemApi.ChangeAttributesOptions.Builder()
    if (lastModifiedTime != null) {
      builder.updateTime(EelFileSystemApi.ChangeAttributesOptions.Builder::modificationTime, lastModifiedTime)
    }
    if (lastAccessTime != null) {
      builder.updateTime(EelFileSystemApi.ChangeAttributesOptions.Builder::accessTime, lastAccessTime)
    }
    fsBlocking {
      api.changeAttributes(path, builder.build()).getOrThrow()
    }
  }
}

internal class IjentNioPosixFileAttributeView(api: IjentFileSystemApi, path: EelPath, nioPath: IjentNioPath) : IjentNioBasicFileAttributeView(api, path, nioPath), PosixFileAttributeView {
  override fun name(): String? {
    return "posix"
  }

  override fun readAttributes(): PosixFileAttributes {
    return Files.readAttributes(nioPath, PosixFileAttributes::class.java)
  }

  override fun setPermissions(perms: Set<PosixFilePermission?>?) {
    if (perms == null) {
      return
    }
    nioPath.nioFs.provider().setAttribute(nioPath, "posix:permissions", perms)
  }

  override fun setGroup(group: GroupPrincipal) {
    if (group is EelPosixGroupPrincipal) {
      nioPath.nioFs.provider().setAttribute(nioPath, "posix:group", group)
    }
    else {
      throw UnsupportedOperationException("Unsupported group principal: $group")
    }
  }

  override fun getOwner(): UserPrincipal? {
    return readAttributes().owner()
  }

  override fun setOwner(owner: UserPrincipal) {
    if (owner is EelPosixUserPrincipal) {
      nioPath.nioFs.provider().setAttribute(nioPath, "posix:owner", owner)
    }
    else {
      throw UnsupportedOperationException("Unsupported user principal: $owner")
    }
  }
}