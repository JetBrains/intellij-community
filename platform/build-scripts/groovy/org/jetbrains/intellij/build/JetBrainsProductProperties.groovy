// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil

/**
 * Describes distribution of an in-house IntelliJ-based IDE hosted in IntelliJ repository.
 */
@CompileStatic
abstract class JetBrainsProductProperties extends ProductProperties {
  {
    scrambleMainJar = true
    productLayout.bundledPluginModules.add("intellij.laf.macos")
    productLayout.bundledPluginModules.add("intellij.laf.win10")
    includeIntoSourcesArchiveFilter = { JpsModule module, BuildContext buildContext ->
      module.contentRootsList.urls.every { url ->
        FileUtil.isAncestor(buildContext.paths.communityHome, JpsPathUtil.urlToOsPath(url), false)
      }
    }
  }
}