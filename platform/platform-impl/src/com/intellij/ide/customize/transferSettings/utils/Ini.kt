// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.utils

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.regex.Pattern

/**
 * TODO: Replace with org.ini4j.Ini
 */
internal class Ini(file: File) {
  private val _section = Pattern.compile("\\s*\\[([^]]*)\\]\\s*")
  private val keyValue = Pattern.compile("\\s*([^=]*)=(.*)")
  private val entries = mutableMapOf<String, MutableMap<String, String>>()

  init {
    BufferedReader(FileReader(file)).use { br ->
      var line: String
      var section: String? = null
      while (br.readLine().also { line = it } != null) {
        var m = _section.matcher(line)
        if (m.matches()) {
          section = m.group(1).trim()
        }
        else if (section != null) {
          m = keyValue.matcher(line)
          if (m.matches()) {
            val key = m.group(1).trim()
            val value = m.group(2).trim()
            var kv = entries[section]
            if (kv == null) {
              entries[section] = mutableMapOf<String, String>().also { kv = it }
            }
            kv!![key] = value
          }
        }
      }
    }
  }

  fun get(section: String, key: String) =  entries[section]?.get(key)
}