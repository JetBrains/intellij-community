// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.project.Project
import java.util.*

// search by full name, not partially (not by words)
class SearchConfigurableByNameHelper(name: String, val rootGroup: ConfigurableGroup) {
  private val names = name.splitToSequence("--", "|").map { it.trim() }.toList()

  private val stack = ArrayDeque<Item>()

  constructor(name: String, project: Project) : this(name, ConfigurableExtensionPointUtil.getConfigurableGroup(project, true))

  fun searchByName(): Configurable? {
    stack.add(Item(rootGroup, null))

    while (true) {
      val item = stack.pollFirst() ?: break
      val result = processChildren(item)
      if (result != null) {
        return result
      }
    }
    return null
  }

  private fun processChildren(parent: Item): Configurable? {
    for (child in parent.configurable.configurables) {
      if (isMatched(child, parent)) {
        return child
      }

      if (child is Configurable.Composite) {
        // do not go deeper until current level is not processed
        stack.add(Item(child, parent))
      }
    }
    return null
  }

  private fun isMatched(child: Configurable, parent: Item): Boolean {
    if (names.size > 1 && parent.parent == null) {
      return false
    }

    if (!names.last().equals(child.displayName, ignoreCase = true)) {
      return false
    }

    var currentParent: Item = parent
    for (i in (names.size - 2) downTo 0) {
      val currentName = (currentParent.configurable as Configurable).displayName
      if (!names[i].equals(currentName, ignoreCase = true)) {
        return false
      }

      currentParent = currentParent.parent ?: return false
    }
    return true
  }
}

private data class Item(val configurable: Configurable.Composite, val parent: Item?)