// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.combined

import com.intellij.diff.editor.DiffVirtualFile

abstract class CombinedDiffVirtualFile<P : CombinedDiffRequestProducer>(protected val requestProducer: P) :
  DiffVirtualFile(requestProducer.name) {

  override fun getPath(): String  = name
}
