// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.jewel.bridge.ComposeSemanticsTreeUtils
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(InternalJewelApi::class)
public class ComposePasteProviderTest {
    @Test
    public fun `paste provider updates on EDT`() {
        assertSame(ActionUpdateThread.EDT, ComposePasteProvider().actionUpdateThread)
    }

    @Test
    public fun `focused component lookup returns null when semantics traversal fails`() {
        assertNull(
            ComposeSemanticsTreeUtils.findFocusedComponent {
                throw NullPointerException("transient Compose semantics tree mutation")
            }
        )
    }

    @Test(expected = ProcessCanceledException::class)
    public fun `focused component lookup does not swallow cancellation`() {
        ComposeSemanticsTreeUtils.findFocusedComponent { throw ProcessCanceledException() }
    }
}
