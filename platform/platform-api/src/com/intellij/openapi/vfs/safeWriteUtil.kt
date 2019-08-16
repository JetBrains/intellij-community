// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName(name = "SafeWriteUtil")
package com.intellij.openapi.vfs

import com.intellij.util.io.SafeFileOutputStream
import com.intellij.util.io.createDirectories
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

fun Path.safeOutputStream(requestor: Any?): OutputStream {
  parent.createDirectories()
  return if (SafeWriteRequestor.shouldUseSafeWrite(requestor)) SafeFileOutputStream(this) else Files.newOutputStream(this)
}