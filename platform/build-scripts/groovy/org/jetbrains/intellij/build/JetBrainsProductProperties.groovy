// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

/**
 * Describes distribution of an in-house IntelliJ-based IDE hosted in IntelliJ repository.
 */
@CompileStatic
abstract class JetBrainsProductProperties extends ProductProperties {
  {
    scrambleMainJar = true
    productLayout.bundledPluginModules = ProductModulesLayout.DEFAULT_BUNDLED_PLUGINS + [
      "intellij.laf.macos",
      "intellij.laf.win10",
    ]
  }
}