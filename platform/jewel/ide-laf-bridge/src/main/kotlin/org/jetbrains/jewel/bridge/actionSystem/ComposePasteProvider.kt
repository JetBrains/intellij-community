// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jewel.bridge.ComposeSemanticsTreeUtils.findFocusedComponent
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.shortcut.JewelActions

@Internal
@InternalJewelApi
public class ComposePasteProvider() : PasteProvider {
    private var findFocused: (ComposePanel) -> SemanticsNode? = { it.findFocusedComponent() }

    init {
        EditActionInputEventTracker.ensureInstalled()
    }

    internal constructor(findFocused: (ComposePanel) -> SemanticsNode?) : this() {
        this.findFocused = findFocused
    }

    override fun performPaste(dataContext: DataContext) {
        performComposeEditAction(
            dataContext,
            JewelActions.Paste,
            IdeActions.ACTION_PASTE,
            SemanticsActions.PasteText,
            findFocused,
        )
    }

    override fun isPastePossible(dataContext: DataContext): Boolean =
        isComposeEditActionEnabled(
            dataContext,
            JewelActions.Paste,
            IdeActions.ACTION_PASTE,
            SemanticsActions.PasteText,
            findFocused,
        )

    override fun isPasteEnabled(dataContext: DataContext): Boolean = isPastePossible(dataContext)

    // This needs to run on the EDT, or we risk synchronization issues like JEWEL-1359
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
