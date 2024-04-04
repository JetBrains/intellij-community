// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.moduleBased

import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData

/**
 * Provide access to the original runtime module repository generated during compilation.
 * Module descriptors point to output directories with class-files and library JARs in the local Maven repository.
 */
interface OriginalModuleRepository {
  val rawRepositoryData: RawRuntimeModuleRepositoryData
  
  val repository: RuntimeModuleRepository
}