// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.envFile

fun parseEnvFile(text: String): Map<String, String> {
  val map = HashMap<String, String>()
  var current: Pair<String, StringBuilder>? = null
  for (line in text.lines()) {
    if (current == null) {
      if (!line.contains('=')) continue
      val split = line.split('=', limit = 2)
      val key = split[0].trim().removePrefix("export ").trim()
      if (split[1].trim().startsWith('"')) {
        val value = stripComment(split[1].trim().removePrefix("\""), '"')
        if (value.endsWith('"')) {
          map.put(key, value.removeSuffix("\""))
        }
        else {
          current = Pair(key, StringBuilder(value))
        }
      }
      else {
        map.put(key, stripComment(split[1], '\'').removeSurrounding("'"))
      }
    }
    else {
      val value = stripComment(line, '"').trim()
      current.second.append(value.removeSuffix("\""))
      if (value.endsWith('"')) {
        map.put(current.first, current.second.toString())
        current = null
      }
    }
  }
  return map
}

private fun stripComment(line: String, delimiter: Char): String {
  val pos = line.lastIndexOf('#')
  if (pos < 0 || pos < line.lastIndexOf(delimiter)) return line.trim()
  return line.substring(0, pos).trim()
}