// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.decodeCachedImageIconFromByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.DataInput
import java.io.DataOutput
import javax.swing.Icon

internal fun readGutterIcon(input: DataInput): Icon? {
  val size = input.readInt()
  if (size == 0) {
    return null
  }

  val byteArray = ByteArray(size)
  input.readFully(byteArray)
  try {
    return decodeCachedImageIconFromByteArray(byteArray)
  }
  catch (e: Throwable) {
    logger<CachedImageIcon>().error(e)
    return null
  }
}

internal fun writeGutterIcon(icon: Icon?, out: DataOutput) {
  if (icon !is CachedImageIcon) {
    out.writeInt(0)
    return
  }

  val data = try {
    icon.encodeToByteArray()
  }
  catch (e: Throwable) {
    logger<CachedImageIcon>().error(e)
    out.write(0)
    return
  }

  out.writeInt(data.size)
  out.write(data)
}