/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.intellij.build.images.sync

internal class Changes(val includeRemoved: Boolean = true) {
  val added: MutableCollection<String> = mutableListOf()
  val modified: MutableCollection<String> = mutableListOf()
  val removed: MutableCollection<String> = mutableListOf()

  fun all() = mutableListOf<String>().also {
    it += added
    it += modified
    if (includeRemoved) it += removed
  }
}