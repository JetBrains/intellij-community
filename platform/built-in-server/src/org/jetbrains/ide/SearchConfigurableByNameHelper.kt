// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.project.Project

// search by full name, not partially (not by words)
internal class SearchConfigurableByNameHelper(name: String, project: Project) {
  private val names = name.splitToSequence('|').map { it.trim() }.toList()
  val rootGroup = ConfigurableExtensionPointUtil.getConfigurableGroup(project, true)

  private val currentNamePath: MutableList<String> = ArrayList()

  private var result: Configurable? = null

  fun searchByName(): Configurable? {
    addChildren(rootGroup)
    return result
  }

  private fun addChildren(parent: Configurable.Composite): Boolean {
    for (child in parent.configurables) {
      if (SearchUtil.isAcceptable(child) && isMatched(child)) {
        result = child
        return false
      }

      if (child is Configurable.Composite) {
        currentNamePath.add(child.displayName)
        if (!addChildren(child)) {
          return false
        }
        currentNamePath.removeAt(currentNamePath.size - 1)
      }
    }

    return true
  }

  private fun isMatched(child: Configurable): Boolean {
    if (child.displayName != names.last()) {
      return false
    }

    var pathIndex = currentNamePath.size - 1
    for (i in (names.size - 2) downTo 0) {
      val currentName = currentNamePath.getOrNull(pathIndex--) ?: return false
      if (currentName != names[i]) {
        return false
      }
    }
    return true
  }
}