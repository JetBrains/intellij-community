// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryUsage

data class LibraryDescriptor(
  /**
   * Name of library/framework
   */
  val libraryName: String,

  /**
   * Root package used to identify the library/framework
   */
  val packagePrefix: String,
)