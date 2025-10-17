// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE")
@file:ApiStatus.Internal
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Unlike [Path], [File] doesn't contain filesystem information, so [File.toPath] will always use local filesystem.
 * To support paths on eel, use this function, i.e:
 * Windows:
 * ```kotlin
 * val unixPath = File("/foo/bar") // Calling `.toPath()` will produce `c:\foo\bar`
 * val nioPath = unixPath.asNio(eelDescriptor) // /foo/bar on eel
 * ```
 * _Warning_: [File] is deprecated and shouldn't be used. Migrate your code to [Path]!
 */
@ApiStatus.Internal
fun File.asNio(onEel: EelDescriptor): Path {
  val parts = path.split(File.separatorChar).filterNot { it.isBlank() }
  if (parts.isEmpty()) return Path("")
  return EelPath.build(parts, onEel).asNioPath()
}