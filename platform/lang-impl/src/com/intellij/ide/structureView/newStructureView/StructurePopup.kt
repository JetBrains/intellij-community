// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.newStructureView

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

@ApiStatus.Experimental
interface StructurePopup: TreeActionsOwner, Disposable {
  fun show()
  fun setTitle(title: @NlsContexts.PopupTitle String)
}

@ApiStatus.Internal
interface StructurePopupProvider {
  /**
   * callbackAfterNavigation doesn't work in the new file structure popup
   */
  fun createPopup(project: Project, fileEditor: FileEditor, callbackAfterNavigation: Consumer<AbstractTreeNode<*>>?): StructurePopup?

  companion object {
    @JvmField
    @ApiStatus.Internal
    val EP: ExtensionPointName<StructurePopupProvider> = ExtensionPointName.create("com.intellij.structurePopupProvider")
  }
}