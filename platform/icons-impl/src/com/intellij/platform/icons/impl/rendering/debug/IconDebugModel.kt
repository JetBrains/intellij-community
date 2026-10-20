// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.debug

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.ScalingContext
import org.jetbrains.annotations.ApiStatus

/**
 * Passed to [IconRenderDebugger.flaggedIconRendered] when a flagged renderer instance renders.
 *
 * @property renderer the flagged [IconRenderer] or `IconLayerRenderer` instance
 * @property subject what [renderer] renders: the [IconDescriptor] for an icon renderer, the `IconLayer` for a layer
 *   renderer (`null` if unknown)
 * @property dimensions the size the renderer reported for this render, in device pixels (`null` if unknown)
 * @property offsetX x offset of the painting context the renderer was given
 * @property offsetY y offset of the painting context the renderer was given
 */
@ApiStatus.Internal
class IconRenderHit(
  val renderer: Any,
  val subject: Any?,
  val scaling: ScalingContext,
  val dimensions: Dimensions?,
  val offsetX: Int,
  val offsetY: Int,
  val threadName: String,
  val timestampNanos: Long,
) {
  override fun toString(): String =
    "IconRenderHit(renderer=${renderer.javaClass.name}@${Integer.toHexString(System.identityHashCode(renderer))}, " +
    "density=${scaling.displayDensity}, contextScale=${scaling.contextScale}, " +
    "dimensions=${dimensions?.let { "${it.width}x${it.height}" }}, offset=($offsetX, $offsetY), thread=$threadName, " +
    "subject=$subject)"
}

/**
 * One renderer invocation recorded by [IconRenderDebugger.capture], with the renderer invocations it made.
 * Holds [renderer] strongly; drop the capture to release it.
 *
 * @property subject the [IconDescriptor] for an icon renderer, the `IconLayer` for a layer renderer, `null` if unknown
 * @property dimensions size reported for this render, in device pixels (`null` if unknown)
 */
@ApiStatus.Internal
class RenderNode(
  val renderer: Any,
  val subject: Any?,
  val scaling: ScalingContext,
  val dimensions: Dimensions?,
  val offsetX: Int,
  val offsetY: Int,
  val children: List<RenderNode>,
) {
  val isIconRenderer: Boolean get() = renderer is IconRenderer
  val rendererClass: String get() = renderer.javaClass.name

  override fun toString(): String = "RenderNode(${renderer.javaClass.simpleName}, subject=$subject, children=${children.size})"
}

/**
 * One captured icon paint (a Swing `SwingIcon.paintIcon` or a Compose draw): the renderer tree that actually ran for it.
 *
 * @property paintTarget the component the icon was painted into (typed `Any?` to keep this module Swing-free); for a
 *   Compose paint, the AWT component hosting the composition, if known
 * @property iconRenderer the top-level renderer of the painted icon
 * @property paintInfo the paint call; for a Compose paint it is derived from [composePaintInfo] (x/y are then pixel
 *   coordinates in the Compose scene, not AWT coordinates)
 * @property roots the renderer invocations made directly by the paint (normally one: the node of [iconRenderer])
 * @property composePaintInfo non-null iff the icon was drawn by Compose (see [IconRenderDebugger.traceComposePaint])
 */
@ApiStatus.Internal
class RenderCapture(
  val paintTarget: Any?,
  val descriptor: IconDescriptor,
  val iconRenderer: Any,
  val paintInfo: SwingPaintInfo,
  val roots: List<RenderNode>,
  val threadName: String,
  val timestampNanos: Long,
  val composePaintInfo: ComposePaintInfo? = null,
) {
  val root: RenderNode? get() = roots.firstOrNull()

  val isCompose: Boolean get() = composePaintInfo != null
}

/**
 * Compose-type-free description of one Compose draw of an icon renderer (Jewel's `Modifier.iconRender`).
 *
 * [x], [y], [width] and [height] are the drawn bounds in pixels of the Compose scene (`positionInWindow`, i.e. the hosting
 * `ComposePanel`'s surface): divide them by the host's system scale (not by [density], which in the IDE also contains the
 * IDE zoom) to get AWT coordinates in the `ComposePanel`.
 *
 * @property hostClass class name of the hosting AWT component, if known
 * @property density the Compose density the icon was resolved with
 * @property scale the icon scale requested by the composable, if any
 * @property finalWidth final width of the resolved icon, in pixels
 * @property finalHeight final height of the resolved icon, in pixels
 */
@ApiStatus.Internal
data class ComposePaintInfo(
  val hostClass: String?,
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float,
  val density: Float,
  val scale: String?,
  val finalWidth: Int,
  val finalHeight: Int,
) {
  /** `true` if the point ([px], [py]), in pixels of the Compose scene, lies inside the drawn bounds. */
  fun contains(px: Float, py: Float): Boolean = px >= x && py >= y && px < x + width && py < y + height
}

/**
 * Swing-type-free description of one `SwingIcon.paintIcon` call.
 */
@ApiStatus.Internal
data class SwingPaintInfo(
  val componentClass: String?,
  val x: Int,
  val y: Int,
  val density: Float,
  val scale: String?,
  val finalWidth: Int,
  val finalHeight: Int,
)

/**
 * Thrown and immediately caught inside [IconRenderDebugger.flaggedIconRendered], purely as a target for a Java Exception
 * Breakpoint (with "Caught exception" enabled). Never escapes.
 */
@ApiStatus.Internal
class IconRenderBreakpoint(val hit: IconRenderHit) : RuntimeException("Flagged icon renderer rendered")
