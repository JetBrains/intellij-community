// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.util.containers.Interner
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

@ApiStatus.Internal
class DeduplicateVisitorsSupplier {
  private val myModuleData: Interner<ModuleData> = Interner.createInterner()
  private val myLibraryData: Interner<LibraryData> = Interner.createInterner()

  fun getVisitor(key: Key<*>): Function<*,*>? = when (key) {
    ProjectKeys.LIBRARY_DEPENDENCY -> Function { dep: LibraryDependencyData? -> visit(dep) }
    ProjectKeys.MODULE_DEPENDENCY -> Function { dep: ModuleDependencyData? -> visit(dep) }
    else -> null
  }

  fun visit(data: LibraryDependencyData?): LibraryDependencyData? {
    if (data == null) {
      return null
    }
    data.ownerModule = myModuleData.intern(data.ownerModule)
    data.target = myLibraryData.intern(data.target)
    return data
  }

  fun visit(data: ModuleDependencyData?): ModuleDependencyData? {
    if (data == null) {
      return null
    }
    data.ownerModule = myModuleData.intern(data.ownerModule)
    data.target = myModuleData.intern(data.target)
    return data
  }
}

