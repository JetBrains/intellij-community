// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.combined

import com.intellij.diff.editor.DiffVirtualFileBase

abstract class CombinedDiffVirtualFile(name: String, private val path: String = name) : DiffVirtualFileBase(name) {
  override fun getPath(): String = path
  abstract fun createModel(): CombinedDiffModel
}

class CombinedDiffVirtualFileImpl(val model: CombinedDiffModel, name: String, path: String = name)
  : CombinedDiffVirtualFile(name, path) {
  override fun createModel(): CombinedDiffModel = model
}
