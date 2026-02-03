// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.moduleBased

import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import java.nio.file.Path

/**
 * Provide access to the original runtime module repository describing the structure of modules before they are packed to JARs in the distribution.
 * Module descriptors point to output directories (or JARs) with class-files and library JARs in the local Maven repository.
 */
interface OriginalModuleRepository {
  val repositoryPath: Path

  val rawRepositoryData: RawRuntimeModuleRepositoryData
  
  val repository: RuntimeModuleRepository
}