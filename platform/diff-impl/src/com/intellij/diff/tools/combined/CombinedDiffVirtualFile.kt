// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.combined

import com.intellij.diff.editor.DiffVirtualFileBase
import org.jetbrains.annotations.ApiStatus

open class CombinedDiffVirtualFile(val sourceId: String, name: String, private val path: String? = null) : DiffVirtualFileBase(name) {
  override fun getPath(): String = path ?: name
}

@ApiStatus.Internal
interface CombinedDiffModelBuilder {
  fun createModel(id: String): CombinedDiffModelImpl
}
