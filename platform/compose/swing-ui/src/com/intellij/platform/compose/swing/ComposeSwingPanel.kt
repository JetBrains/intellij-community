// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import androidx.compose.runtime.Composable
import com.intellij.internal.inspector.UiInspectorUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.ui.WidthBasedLayout
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.core.SwingRecomposer
import org.jetbrains.compose.swing.foundation.layout.ConstrainedPanel
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.MeasurePolicy
import org.jetbrains.compose.swing.foundation.layout.setContent
import javax.swing.JComponent

/**
 * Creates a Swing [JComponent] hosting the given Compose [content].
 *
 * The content joins the composition the host's own place in the Swing hierarchy resolves to: an enclosing
 * composition when the host is nested under one, otherwise the one its window shares. Every island in a
 * window then recomposes on that window's single recomposer and frame clock, and the clock is paced to the
 * display the window is on. The content is mounted once the host reaches a window, and a host disposed before
 * that mounts nothing.
 *
 * The composition belongs to [parentDisposable] and to nothing else. It is torn down when that is
 * disposed, and never by the host leaving the Swing hierarchy, so a host that is taken out of one
 * container and put into another - a tool window whose tab is switched away and back - keeps the state it
 * remembered and the effects it had running. A host whose disposable is never disposed keeps its
 * composition for the lifetime of the process.
 *
 * Composable code and effects run on the EDT with no read or write lock held and `ModalityState.any()`
 * semantics. They may read and write Swing and snapshot state freely, and must not touch PSI, VFS,
 * documents or the project model directly: reach the model from a `LaunchedEffect` through `readAction`, a
 * suspending service, or a flow collected into snapshot state.
 *
 * Must be called on the EDT, and [parentDisposable] must be disposed on the EDT.
 *
 * The host is the root of the content's layout, as the root of an androidx Compose window is: each top-level
 * composable is measured under the host's size and placed at its top-start corner, so layout modifiers such as
 * `fillMaxWidth` or `width` apply at the top level. The host implements [WidthBasedLayout], so an IDE layout that
 * asks for the height at a given width gets the content's height at that width.
 *
 * This is the Swing-Compose analogue of the Jewel `JewelComposePanel`.
 *
 * @see org.jetbrains.jewel.bridge.JewelComposePanel
 */
@ApiStatus.Experimental
public fun composeSwingPanel(
  parentDisposable: Disposable,
  content: @Composable ConstrainedScope.() -> Unit,
): JComponent {
  val panel = ComposeSwingPanel()
  val handle = panel.setContent(content = content)
  Disposer.register(parentDisposable, Disposable { handle.dispose() })
  return panel
}

/**
 * Sets [content] as [setContent] does, on a recomposer of this panel's own, so the content composes on this call and
 * needs no window: a host whose size or components are read before it shows, such as a Settings page that settings
 * search walks, or a popup that reads its size before it opens. Disposing the result ends the content and the
 * recomposer.
 */
@OptIn(InternalSwingUiApi::class)
internal fun ConstrainedPanel.setContentWithOwnRecomposer(content: @Composable ConstrainedScope.() -> Unit): Disposable {
  val recomposer = SwingRecomposer.create(this)
  val composition = try {
    setContent(recomposer.compositionContext, content)
  }
  catch (e: Throwable) {
    recomposer.dispose()
    throw e
  }
  return Disposable {
    composition.dispose()
    recomposer.dispose()
  }
}

@ApiStatus.Internal
public open class ComposeSwingPanel : ConstrainedPanel, WidthBasedLayout {
  public constructor() : super()

  /** A host whose top-level composables [measurePolicy] measures and places. */
  internal constructor(measurePolicy: MeasurePolicy) : super(measurePolicy)

  init {
    // The panel paints the panel background, as the JPanel the host replaces did.
    isOpaque = true
    background = JBColor.PanelBackground
    registerCreationStacktrace()
  }

  override fun getPreferredWidth(): Int = maxIntrinsicWidth(Constraints.Infinity)

  override fun getPreferredHeight(width: Int): Int = maxIntrinsicHeight(width)

  private fun registerCreationStacktrace() {
    if (ApplicationManager.getApplication()?.isInternal == true && UiInspectorUtil.isSaveStacktraces()) {
      ClientProperty.put(this, CREATION_STACKTRACE, Throwable())
    }
  }

  @ApiStatus.Internal
  public companion object {
    @JvmStatic
    public val CREATION_STACKTRACE: Key<Throwable> = Key.create("compose.swing.panel.creation.stacktrace")
  }
}
