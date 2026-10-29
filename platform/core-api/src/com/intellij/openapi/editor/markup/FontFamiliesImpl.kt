// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import java.util.Collections

/**
 * The only [FontFamilies] implementation.
 * [create] checks and copies the names. It does not share instances, so equal names give equal values that are not the same object.
 */
internal class FontFamiliesImpl private constructor(
  private val names: List<String>,
) : FontFamilies {
  private val hash: Int = names.hashCode()

  override fun names(): List<String> {
    return names
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    return other is FontFamiliesImpl && hash == other.hash && names == other.names
  }

  override fun hashCode(): Int {
    return hash
  }

  override fun toString(): String {
    return names.joinToString(prefix = "FontFamilies[", postfix = "]")
  }

  companion object {
    fun create(names: List<String>): FontFamilies {
      val copy = Collections.unmodifiableList(ArrayList(names))
      requireValidNames(copy)
      return FontFamiliesImpl(copy)
    }

    /**
     * Takes nullable names, because a Java caller can pass a `null` name.
     */
    private fun requireValidNames(names: List<String?>) {
      require(names.isNotEmpty()) { "No font family names" }
      val uniqueNames = HashSet<String>(names.size)
      for (name in names) {
        require(name != null) { "A null font family name in $names" }
        require(name.isNotBlank()) { "A blank font family name in $names" }
        require(name.trim() == name) { "A font family name with leading or trailing spaces: '$name'" }
        require(',' !in name) { "A font family name with a comma: '$name'" }
        require(uniqueNames.add(name)) { "A repeated font family name: '$name'" }
      }
    }
  }
}
