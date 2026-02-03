// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure

import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener

@Internal
interface TreeStateListener : TreeExpansionListener {

  fun treeStateRestoreStarted(event: TreeExpansionEvent)
  fun treeStateCachedStateRestored(event: TreeExpansionEvent)
  fun treeStateRestoreFinished(event: TreeExpansionEvent)

}
