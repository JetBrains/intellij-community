/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.intellij.build.images.sync

internal class Changes(val includeRemoved: Boolean = true) {
  enum class Type {
    MODIFIED, ADDED, DELETED
  }

  val added: MutableCollection<String> = mutableListOf()
  val modified: MutableCollection<String> = mutableListOf()
  val removed: MutableCollection<String> = mutableListOf()

  fun all() = mutableListOf<String>().also {
    it += added
    it += modified
    if (includeRemoved) it += removed
  }

  fun clear() {
    added.clear()
    modified.clear()
    removed.clear()
  }

  fun register(type: Type, changes: Collection<String>) {
    added.removeAll(changes)
    modified.removeAll(changes)
    removed.removeAll(changes)
    when (type) {
      Type.ADDED -> added += changes
      Type.MODIFIED -> modified += changes
      Type.DELETED -> removed += changes
    }
  }
}