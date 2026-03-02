// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView

import com.intellij.openapi.Disposable
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
interface StructurePopupTestExt: Disposable {
  @TestOnly
  @ApiStatus.Internal
  fun getSpeedSearch(): TreeSpeedSearch?

  @TestOnly
  @ApiStatus.Internal
  fun setSearchFilterForTests(filter: String?)

  @TestOnly
  @ApiStatus.Internal
  fun setTreeActionState(actionName: String, state: Boolean)

  @TestOnly
  @ApiStatus.Internal
  fun initUi()

  @TestOnly
  @ApiStatus.Internal
  fun getTree(): Tree
}