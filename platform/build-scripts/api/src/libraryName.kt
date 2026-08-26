// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import kotlin.io.path.name

/**
 * Returns the name the distribution knows a library by.
 *
 * A named library gives its own name. An unnamed module library has a `#` prefix, and it gives the file name of its
 * single JAR.
 */
@Internal
fun getLibraryFileName(lib: JpsLibrary): String {
  val name = lib.name
  if (name.startsWith('#')) {
    // unnamed module libraries in the IntelliJ project may have only one root
    val paths = lib.getPaths(JpsOrderRootType.COMPILED)
    require(paths.size == 1) {
      "Unnamed module library has more than one element: $paths"
    }
    return paths[0].name
  }
  return name
}
