// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModel.ElementInfoProvider
import com.intellij.ide.util.treeView.smartTree.TreeModel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun getElementInfoProvider(tree: TreeModel): ElementInfoProvider? {
  if (tree is ElementInfoProvider) {
    return tree
  }
  if (tree is TreeModelWrapper) {
    val model: StructureViewModel? = tree.model
    if (model is ElementInfoProvider) {
      return model
    }
  }
  return null
}