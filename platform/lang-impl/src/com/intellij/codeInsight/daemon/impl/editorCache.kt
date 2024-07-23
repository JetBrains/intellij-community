// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.decodeCachedImageIconFromByteArray
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

internal fun writeGutterIcon(out: DataOutput, icon: Icon?) {
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