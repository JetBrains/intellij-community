// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ModifiableWorkspaceModel {

  fun updateLibrarySubstitutions()

  fun isLibrarySubstituted(library: Library): Boolean

  fun commit()

  companion object {

    val NOP = object : ModifiableWorkspaceModel {
      override fun updateLibrarySubstitutions() = Unit
      override fun isLibrarySubstituted(library: Library): Boolean = false
      override fun commit() = Unit
    }
  }
}
