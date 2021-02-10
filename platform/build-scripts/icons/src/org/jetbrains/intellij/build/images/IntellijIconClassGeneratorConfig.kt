// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images

import org.jetbrains.jps.model.module.JpsModule

class IntellijIconClassGeneratorConfig : IconsClasses() {
  override val modules: List<JpsModule>
    get() = super.modules.filterNot {
      // TODO: use icon-robots.txt
      it.name.startsWith("fleet")
    }

  override fun getConfigForModule(moduleName: String): IntellijIconClassGeneratorModuleConfig? {
    @Suppress("SpellCheckingInspection")
    return when (moduleName) {
      // force generating "Groovy" inner class to preserve backward compatiblity
      "intellij.groovy.psi" -> IntellijIconClassGeneratorModuleConfig(className = "Jetgroovy", iconDirectory = "icons")
      else -> super.getConfigForModule(moduleName)
    }
  }
}