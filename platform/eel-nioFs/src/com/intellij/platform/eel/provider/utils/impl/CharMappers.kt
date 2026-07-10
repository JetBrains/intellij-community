// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils.impl

import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap

// Converts special chars from ijent@unix to local Windows and vice versa, see IJPL-213371
// JVM nio on Windows doesn't allow `:` and `|`, but *nix fs supports them.
// 9P in WSL converts them to special chars, so do we

private val enabled by lazy {
  LocalEelDescriptor.osFamily.isWindows
}
private val mappings = arrayOf(':' to 0xF03A.toChar(), '|' to 0xF07C.toChar())
private val unixToWinMap =
  Int2IntArrayMap(mappings.map { it.first.code }.toIntArray(), mappings.map { it.second.code }.toIntArray(), mappings.size)
private val winToUnixMap = Int2IntArrayMap(unixToWinMap.values.toIntArray(), unixToWinMap.keys.toIntArray(), unixToWinMap.size)

private fun convert(fileName: String, mapping: Int2IntArrayMap): String {
  if (!enabled) {
    return fileName
  }
  var result: StringBuilder? = null
  for ((i, char) in fileName.withIndex()) {
    val codeToReplace = mapping[char.code]
    if (codeToReplace != 0) {
      val builder = result ?: StringBuilder(fileName).also { result = it }
      builder[i] = codeToReplace.toChar()
    }
  }
  return result?.toString() ?: fileName
}

/**
 * When [fileName] came from ijent/gprc, convert it to local (nio-compatible) name
 */

@EelDelicateApi
fun ijentToLocal(fileName: String): String = convert(fileName, unixToWinMap)

/**
 * When [fileName] came from local (i.e. nio) convert it to ijent/gprc compatible name
 */

@EelDelicateApi
fun localToIjent(fileName: String): String = convert(fileName, winToUnixMap)
