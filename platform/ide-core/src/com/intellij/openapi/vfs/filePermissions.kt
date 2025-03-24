// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.platform.ide.core.permissions.Permission
import com.intellij.platform.ide.core.permissions.impl.IdePermission
import com.intellij.openapi.vfs.impl.ReadFilePermission
import com.intellij.openapi.vfs.impl.WriteFilePermission
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
val projectFilesRead: Permission = IdePermission("ide.access.files.read")

@ApiStatus.Experimental
val projectFilesWrite: Permission = IdePermission("ide.access.files.write")

@ApiStatus.Experimental
fun readFilePermission(file: VirtualFile): Permission = ReadFilePermission(file)

@ApiStatus.Experimental
fun writeFilePermission(file: VirtualFile): Permission = WriteFilePermission(file)