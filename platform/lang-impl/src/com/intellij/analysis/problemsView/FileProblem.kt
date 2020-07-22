// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView

import com.intellij.openapi.vfs.VirtualFile

interface FileProblem : Problem {
  /**
   * The file that the problem belongs to.
   */
  val file: VirtualFile

  /**
   * The offset in the file for navigation.
   */
  val offset: Int
    get() = -1
}
