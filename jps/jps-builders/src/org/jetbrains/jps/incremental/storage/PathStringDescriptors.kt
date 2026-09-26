// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PathStringDescriptors")
package org.jetbrains.jps.incremental.storage

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun createPathStringDescriptor(): KeyDescriptor<String> {
  return PathStringDescriptor
}

private object PathStringDescriptor : EnumeratorStringDescriptor() {
  override fun getHashCode(value: String?): Int = FileUtilRt.pathHashCode(value)

  override fun isEqual(val1: String?, val2: String?): Boolean = FileUtil.pathsEqual(val1, val2)
}