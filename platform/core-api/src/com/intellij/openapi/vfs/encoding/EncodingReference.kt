// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding

import com.intellij.openapi.vfs.CharsetToolkit
import java.nio.charset.Charset

/**
 * Represents either an actual [charset] or `null` meaning
 * that [CharsetToolkit.getDefaultSystemCharset] should be used instead
 */
data class EncodingReference(val charset: Charset?) {
  /**
   * Create [EncodingReference] from charsetName. Uses [DEFAULT] if encoding with provided name not found
   * see [com.intellij.openapi.vfs.CharsetToolkit.forName]
   * @param charsetName charset name or null for default
   */
  constructor(charsetName: String?) : this(CharsetToolkit.forName(charsetName))

  fun dereference(): Charset {
    return charset ?: CharsetToolkit.getDefaultSystemCharset()
  }

  companion object {
    @JvmField
    val DEFAULT: EncodingReference = EncodingReference(charset = null)
  }
}