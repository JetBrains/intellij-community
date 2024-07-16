// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.nameGenerator

import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path

@ApiStatus.Internal
object ModuleNameGenerator {

  @JvmStatic
  fun generate(moduleData: ModuleData, delimiter: String): Iterable<String> {
    var modulePath = File(moduleData.linkedExternalProjectPath)
    if (modulePath.isFile) {
      modulePath = modulePath.parentFile
    }
    return generate(moduleData.group, moduleData.internalName, modulePath.toPath(), delimiter)
  }

  @JvmStatic
  fun generate(group: String?, name: String, path: Path, delimiter: String): Iterable<String> {
    return SimpleNameGenerator.generate(group, name, delimiter) +
           PathNameGenerator.generate(name, path, delimiter) +
           NumericNameGenerator.generate(name)
  }
}
