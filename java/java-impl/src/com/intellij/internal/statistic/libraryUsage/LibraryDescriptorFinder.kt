// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryUsage

interface LibraryDescriptorFinder {
  /**
   * @param packageQualifier is a package like `a.b.c.d`. The separator is `.`
   * @return library name if package prefix matches
   */
  fun findSuitableLibrary(packageQualifier: String): String?
}
