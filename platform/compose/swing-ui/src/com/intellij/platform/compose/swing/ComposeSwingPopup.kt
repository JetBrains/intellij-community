// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import androidx.compose.runtime.Composable
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ScreenUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.IntrinsicMeasurable
import org.jetbrains.compose.swing.foundation.layout.IntrinsicMeasureScope
import org.jetbrains.compose.swing.foundation.layout.Measurable
import org.jetbrains.compose.swing.foundation.layout.MeasurePolicy
import org.jetbrains.compose.swing.foundation.layout.MeasureResult
import org.jetbrains.compose.swing.foundation.layout.MeasureScope
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Creates a [JBPopup] from this factory, with the composable [content] as its content.
 *
 * The popup takes the size of the content and follows it when the content changes. The content states its own limits
 * with layout modifiers such as `width` or `heightIn`, since the popup offers it no limit of its own. The popup window
 * stops at the screen, so a content larger than the screen is cut off. [positionProvider]
 * places the popup against the component it is shown for, when it shows and on each change of its size. Without a
 * provider, or while that component is not showing, the popup keeps its top-left corner.
 *
 * The content composes on this call, so the popup reads the size of the content before it shows. The composition
 * ends when the popup is disposed, so a popup that never shows must be disposed by the caller.
 *
 * Must be called on the EDT.
 *
 * @param positionProvider where the popup goes, or `null` to keep the place that `show` gives it.
 * @param dataProvider the data the content gives to actions, or `null` for none.
 * @param configure sets the options of the popup on the platform popup builder. It must not create the popup.
 * @param content the content of the popup.
 */
@ApiStatus.Experimental
public fun JBPopupFactory.createComposeSwingPopup(
  positionProvider: PopupPositionProvider? = null,
  dataProvider: UiDataProvider? = null,
  configure: ComponentPopupBuilder.() -> Unit = {},
  content: @Composable ConstrainedScope.() -> Unit,
): JBPopup {
  val host = ComposeSwingPopupHost(positionProvider)
  val composition = host.setContentWithOwnRecomposer(content)
  val created = try {
    createComponentPopupBuilder(host.withData(dataProvider), null).apply(configure).createPopup()
  }
  catch (e: Throwable) {
    Disposer.dispose(composition)
    throw e
  }
  Disposer.register(created, composition)
  host.popup = created
  return created
}

/**
 * Creates a [JComponent] that hosts the composable [content] as the content of a popup that the platform builds, such
 * as the component a documentation or hover factory returns. The platform sizes that popup.
 *
 * The content composes on this call, so the platform reads the size of the content before the popup shows. The
 * composition ends when [parentDisposable] is disposed.
 *
 * Must be called on the EDT, and [parentDisposable] must be disposed on the EDT.
 *
 * @param parentDisposable the owner of the composition.
 * @param dataProvider the data the content gives to actions, or `null` for none.
 * @param content the content of the popup.
 */
@ApiStatus.Experimental
public fun composeSwingPopupContent(
  parentDisposable: Disposable,
  dataProvider: UiDataProvider? = null,
  content: @Composable ConstrainedScope.() -> Unit,
): JComponent {
  val host = ComposeSwingPanel()
  Disposer.register(parentDisposable, host.setContentWithOwnRecomposer(content))
  return host.withData(dataProvider)
}

/** Calculates where a popup of [createComposeSwingPopup] goes on the screen. */
@ApiStatus.Experimental
public interface PopupPositionProvider {
  /**
   * Returns the screen location of the top-left corner of the popup window.
   *
   * The popup then moves back onto [screenBounds] where the location puts a part of it off the screen.
   *
   * @param anchorBounds the bounds of the component the popup is shown for, in screen coordinates.
   * @param screenBounds the bounds of the screen that holds the anchor.
   * @param orientation the orientation of the component the popup is shown for.
   * @param popupSize the size of the popup window.
   */
  public fun calculatePosition(
    anchorBounds: Rectangle,
    screenBounds: Rectangle,
    orientation: ComponentOrientation,
    popupSize: Dimension,
  ): Point
}

/**
 * Puts the [popupAlignment] point of the popup on the [anchorAlignment] point of the anchor, then moves the popup by
 * [offset]. A right-to-left anchor mirrors both alignments and the horizontal offset.
 *
 * ```
 * // Above the anchor, with the end edges in line.
 * AnchorAlignmentPositionProvider(anchorAlignment = Alignment.TopEnd, popupAlignment = Alignment.BottomEnd)
 * ```
 */
@ApiStatus.Experimental
public class AnchorAlignmentPositionProvider(
  private val anchorAlignment: Alignment,
  private val popupAlignment: Alignment,
  private val offset: Point = Point(),
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: Rectangle,
    screenBounds: Rectangle,
    orientation: ComponentOrientation,
    popupSize: Dimension,
  ): Point {
    val anchorPoint = anchorAlignment.align(Dimension(), anchorBounds.size, orientation)
    val popupPoint = popupAlignment.align(Dimension(), popupSize, orientation)
    val offsetX = if (orientation.isLeftToRight) offset.x else -offset.x
    return Point(
      anchorBounds.x + anchorPoint.x - popupPoint.x + offsetX,
      anchorBounds.y + anchorPoint.y - popupPoint.y + offset.y,
    )
  }
}

/**
 * The host of a [createComposeSwingPopup] content.
 *
 * While its popup shows, the host is the validate root of the content. A change of the size of the content then reaches
 * its [validate], which gives the popup that size before the content is laid out.
 */
internal class ComposeSwingPopupHost(private val positionProvider: PopupPositionProvider?) : ComposeSwingPanel(PopupContentMeasurePolicy) {
  var popup: JBPopup? = null
  private var validating = false

  private val showingPopup: JBPopup? get() = popup?.takeIf { it.isVisible }

  // The IDE popup is sized before it is shown, so addNotify places it before the first frame.
  override fun addNotify() {
    super.addNotify()
    val provider = positionProvider ?: return
    val popup = popup ?: return
    val window = SwingUtilities.getWindowAncestor(this) ?: return
    popup.place(window, window.size, provider)
  }

  override fun isValidateRoot(): Boolean = showingPopup != null

  override fun validate() {
    validating = true
    try {
      if (!isValid) showingPopup?.followContent(this, positionProvider)
      super.validate()
    }
    finally {
      validating = false
    }
  }

  override fun validateTree() {
    super.validateTree()
    // An ancestor that validates first lays the host out at its old size and leaves it valid. A new validation of the
    // host gives the popup the size of the content then.
    if (!validating && showingPopup != null && preferredSize != size) revalidate()
  }
}

/**
 * The measure policy of a popup host. It measures the top-level composables at the size they take for themselves, not
 * at the size of the host, and places them at the top-start corner. The host prefers that size at any width.
 */
internal object PopupContentMeasurePolicy : MeasurePolicy {
  override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
    val placeables = measurables.map { it.measure(constraints) }
    val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
    val height = (placeables.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
    return layout(width, height) { placeables.forEach { it.placeRelative(0, 0) } }
  }

  override fun MeasureScope.measureAtAssignedSize(measurables: List<Measurable>, width: Int, height: Int): MeasureResult {
    val placeables = measurables.map { it.measure(Constraints()) }
    return layout(width, height) { placeables.forEach { it.placeRelative(0, 0) } }
  }

  override fun IntrinsicMeasureScope.preferredSize(measurables: List<IntrinsicMeasurable>, assignedWidth: Int?): Dimension {
    val width = measurables.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
    return Dimension(width, measurables.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0)
  }
}

private fun ComposeSwingPanel.withData(dataProvider: UiDataProvider?): JComponent =
  if (dataProvider == null) this else UiDataProvider.wrapComponent(this, dataProvider)

/** Gives the popup the preferred size of [host], the size of its content, and places it by [positionProvider]. */
private fun JBPopup.followContent(host: JComponent, positionProvider: PopupPositionProvider?) {
  val contentSize = host.preferredSize
  if (contentSize == host.size) return
  val window = SwingUtilities.getWindowAncestor(host) ?: return
  if (positionProvider != null) {
    // pack keeps the popup on the screen from the place it has, so the popup first goes where its new size puts it.
    place(window, Dimension(window.width + contentSize.width - host.width, window.height + contentSize.height - host.height), positionProvider)
  }
  pack(true, true)
  positionProvider?.let { place(window, window.size, it) }
  // pack and setLocation set the window bounds and lay nothing out. The window lays the content out at its new size
  // here, so the content shows no frame at the old size.
  window.validate()
}

/** Places the popup window [window] of [windowSize] against the owner of the popup by [positionProvider]. */
private fun JBPopup.place(window: Window, windowSize: Dimension, positionProvider: PopupPositionProvider) {
  val owner = owner?.takeIf { it.isShowing } ?: return
  val anchor = Rectangle(owner.locationOnScreen, owner.size)
  val bounds = popupWindowBounds(positionProvider, anchor, ScreenUtil.getScreenRectangle(anchor.location), owner.componentOrientation, windowSize)
  setLocation(popupLocationArgument(bounds.location, window.location, locationOnScreen, content.insets))
}

/**
 * The point to pass to [JBPopup.setLocation] so that the popup window at [window] moves to [target].
 *
 * The popup reports [reported] from [JBPopup.getLocationOnScreen], and [insets] are the insets of [JBPopup.getContent].
 * `getLocationOnScreen` adds the insets to the window location and takes off the header offset of a popup located by
 * its content. `setLocation` takes off both, so the point adds both to [target].
 */
internal fun popupLocationArgument(target: Point, window: Point, reported: Point, insets: Insets): Point =
  Point(target.x + 2 * insets.left - (reported.x - window.x), target.y + 2 * insets.top - (reported.y - window.y))

/** The bounds of a popup window of [popupSize] that [provider] places against [anchorBounds], moved onto [screenBounds]. */
internal fun popupWindowBounds(
  provider: PopupPositionProvider,
  anchorBounds: Rectangle,
  screenBounds: Rectangle,
  orientation: ComponentOrientation,
  popupSize: Dimension,
): Rectangle {
  val bounds = Rectangle(provider.calculatePosition(anchorBounds, screenBounds, orientation, popupSize), popupSize)
  ScreenUtil.moveToFit(bounds, screenBounds, null)
  return bounds
}
