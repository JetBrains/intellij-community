// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

data class ModuleLibraryData(
  val moduleName: String,
  val libraryName: String,
  val relativeOutputPath: String,
)
