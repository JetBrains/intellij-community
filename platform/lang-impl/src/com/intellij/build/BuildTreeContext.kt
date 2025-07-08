// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val BUILD_TREE_SELECTED_NODE: DataKey<SelectedBuildTreeNode> = DataKey.create("BuildTreeSelectedNode")

@ApiStatus.Internal
@Serializable
data class SelectedBuildTreeNode(val nodeId: Int)