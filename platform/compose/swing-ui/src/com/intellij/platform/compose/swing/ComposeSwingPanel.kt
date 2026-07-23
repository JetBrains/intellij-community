// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import androidx.compose.runtime.Composable
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.setContent
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Creates a Swing [JComponent] hosting the given Compose [content].
 *
 * The composition belongs to [parentDisposable] and to nothing else. It is torn down when that is
 * disposed, and never by the host leaving the Swing hierarchy, so a host that is taken out of one
 * container and put into another - a tool window whose tab is switched away and back - keeps the state it
 * remembered and the effects it had running. A host whose disposable is never disposed keeps its
 * composition for the lifetime of the process.
 *
 * The content is mounted once the host reaches a window, and a host disposed before that mounts nothing.
 * A page built off-screen therefore holds no components until it is shown, which is what settings search
 * sees when it walks a page it never displayed.
 *
 * Composable code and effects run on the EDT with no read or write lock held and `ModalityState.any()`
 * semantics. They may read and write Swing and snapshot state freely, and must not touch PSI, VFS,
 * documents or the project model directly: reach the model from a `LaunchedEffect` through `readAction`, a
 * suspending service, or a flow collected into snapshot state.
 *
 * Must be called on the EDT, and [parentDisposable] must be disposed on the EDT.
 *
 * This is the Swing-Compose analogue of the Jewel `JewelComposePanel`.
 *
 * @see org.jetbrains.jewel.bridge.JewelComposePanel
 */
@ApiStatus.Experimental
public fun composeSwingPanel(
  parentDisposable: Disposable,
  content: @Composable () -> Unit,
): JComponent {
  val panel = JPanel(BorderLayout())
  val handle = panel.setContent(content)
  Disposer.register(parentDisposable, Disposable { handle.dispose() })
  return panel
}
