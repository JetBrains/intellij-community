// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import java.nio.file.Path

internal class ModuleOutputProviderIndex(
  val modules: List<JpsModule>,
) {
  private val nameToModule = modules.associateByTo(HashMap(modules.size)) { it.name }
  private val projectLibraryToModuleMapCache by lazy { buildProjectLibraryToModuleMap(modules) }

  fun findModule(name: String): JpsModule? = nameToModule[name.removeSuffix("._test")]

  fun findRequiredModule(name: String): JpsModule {
    return requireNotNull(findModule(name)) {
      "Cannot find required module '$name' in the project"
    }
  }

  fun getProjectLibraryToModuleMap(): Map<String, String> = projectLibraryToModuleMapCache

  fun getModuleImlFile(module: JpsModule): Path {
    val baseDir = requireNotNull(JpsModelSerializationDataService.getBaseDirectoryPath(module)) {
      "Cannot find base directory for module ${module.name}"
    }
    return baseDir.resolve("${module.name}.iml")
  }
}
