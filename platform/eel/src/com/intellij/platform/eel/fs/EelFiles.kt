// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.EelSharedSecrets
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Path

/**
 * A drop-in replacement for some methods of [java.nio.file.Files].
 *
 * See also [EelFileUtils].
 */
@ApiStatus.Experimental
object EelFiles {
  /**
   * Does the same as [java.nio.file.Files.readAllBytes] but works more effectively with Eel.
   * In particular, it performs fewer RPC requests to IJent for reading files.
   */
  @JvmStatic
  @Throws(IOException::class)
  fun readAllBytes(path: Path): ByteArray = EelSharedSecrets.filesImpl.readAllBytes(path)

  /**
   * Does the same as [java.nio.file.Files.readString] but works more effectively with Eel.
   * In particular, it performs fewer RPC requests to IJent for reading files.
   */
  @JvmStatic
  @Throws(IOException::class)
  fun readString(path: Path): String =
    readString(path, Charsets.UTF_8)

  /**
   * Does the same as [java.nio.file.Files.readString] but works more effectively with Eel.
   * In particular, it performs fewer RPC requests to IJent for reading files.
   */
  @JvmStatic
  @Throws(IOException::class)
  fun readString(path: Path, cs: Charset): String = EelSharedSecrets.filesImpl.readString(path, cs)
}