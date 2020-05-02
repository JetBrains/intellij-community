// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots

import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable

class ProjectLevelLibrariesInRootModelTest : LibrariesFromLibraryTableInRootModelTestCase() {
  override val libraryTable: LibraryTable
    get() = projectModel.projectLibraryTable

  override fun createLibrary(name: String): Library = projectModel.addProjectLevelLibrary(name)
}