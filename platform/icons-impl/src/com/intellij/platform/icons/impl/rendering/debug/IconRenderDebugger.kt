// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.debug

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.rendering.layers.IconLayerRenderer
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.ScalingContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

/**
 * Debug hook in the icon render path, used by the internal Icon Inspector. Works on renderer INSTANCES: two renderers of
 * equal descriptors are different targets.
 *
 * - [capture] records the tree of renderer instances that actually run when an icon is painted into a chosen component.
 * - [flag] one of those instances (an [IconRenderer] or [IconLayerRenderer]) to make each of its renders call
 *   [flaggedIconRendered], the debugger stop point. Flagged renderers are held weakly and compared by identity.
 *
 * Render sites check [isActive] first and only then call [traceRender] / [traceLayer] / [traceSwingPaint] /
 * [traceComposePaint], which bracket the actual render call; so while nothing is flagged or captured, the whole cost is one
 * volatile read. Captures start at a top-level paint: `SwingIcon.paintIcon` (Swing) or Jewel's icon draw node (Compose).
 *
 * Listeners run on the rendering thread (usually the EDT) and must be fast. Renders triggered from inside a listener or a
 * capture filter are not traced, so they can safely render icons.
 */
@ApiStatus.Internal
object IconRenderDebugger {
  @Volatile
  private var active: Boolean = false

  /** `true` iff at least one renderer is flagged or at least one capture is open. */
  @JvmStatic
  val isActive: Boolean
    get() = active

  private val lock = Any()
  private val flags = WeakIdentityCounters() // guarded by lock
  private val captures = CopyOnWriteArrayList<Capture>()
  private val hitListeners = CopyOnWriteArrayList<(IconRenderHit) -> Unit>()
  private val redrawListeners = CopyOnWriteArrayList<(Any?) -> Unit>()
  private val threadState = ThreadLocal.withInitial { ThreadState() }

  private val logger: System.Logger by lazy { System.getLogger(IconRenderDebugger::class.java.name) }

  // ---- flags ----

  /** Flags [renderer] (an [IconRenderer] or [IconLayerRenderer] instance); it is held weakly. */
  fun flag(renderer: Any) {
    synchronized(lock) {
      flags.add(renderer)
      updateActive()
    }
  }

  fun unflag(renderer: Any) {
    synchronized(lock) {
      flags.remove(renderer)
      updateActive()
    }
  }

  fun isFlagged(renderer: Any): Boolean = synchronized(lock) { flags.get(renderer) != null }

  /** Number of renders of [renderer] since it was flagged; `0` if it is not flagged. */
  fun hitCount(renderer: Any): Int = synchronized(lock) { flags.get(renderer)?.get() ?: 0 }

  /** Currently flagged renderers that are still alive. */
  fun flaggedRenderers(): List<Any> = synchronized(lock) { flags.keys() }

  /** Removes all flags and captures. Hit listeners, redraw listeners and location describers are kept. */
  fun clearAll() {
    synchronized(lock) {
      flags.clear()
      captures.clear()
      updateActive()
    }
  }

  /**
   * Notifies [listener] about every [IconRenderHit] (after [flaggedIconRendered] was reached), until the handle is
   * closed. Does not by itself activate the debugger.
   */
  fun addHitListener(listener: (IconRenderHit) -> Unit): AutoCloseable {
    hitListeners.add(listener)
    return AutoCloseable { hitListeners.remove(listener) }
  }

  // ---- capture ----

  /**
   * Until the returned handle is closed, every icon paint (Swing or Compose) whose paint target (the component, or the AWT
   * host of a composition) passes [filter] is recorded as a [RenderCapture] and handed to [listener]. Keeping a capture open
   * gives live updates.
   */
  fun capture(filter: (paintTarget: Any?) -> Boolean, listener: (RenderCapture) -> Unit): AutoCloseable {
    val capture = Capture(filter, listener)
    synchronized(lock) {
      captures.add(capture)
      updateActive()
    }
    return AutoCloseable {
      synchronized(lock) {
        captures.remove(capture)
        updateActive()
      }
    }
  }

  private fun updateActive() {
    active = !flags.isEmpty() || captures.isNotEmpty()
  }

  // ---- render-path hooks; call only when isActive ----

  /**
   * Brackets one Swing paint of an icon whose top-level renderer is [renderer]: starts a capture if an open one accepts
   * [paintTarget], runs [paint], then delivers the capture.
   */
  @JvmStatic
  fun traceSwingPaint(renderer: Any, descriptor: IconDescriptor, paintTarget: Any?, info: SwingPaintInfo, paint: () -> Unit) {
    tracePaint(renderer, descriptor, paintTarget, { info to null }, paint)
  }

  /**
   * Compose counterpart of [traceSwingPaint]: brackets one Compose draw of an icon whose top-level renderer is [renderer].
   * [paintTarget] is the AWT component hosting the composition (`null` if unknown). [info] is only computed when an open
   * capture accepts [paintTarget], so it may do some work (e.g. compute the icon's position in the Compose scene).
   */
  @JvmStatic
  fun traceComposePaint(renderer: Any, descriptor: IconDescriptor, paintTarget: Any?, info: () -> ComposePaintInfo, paint: () -> Unit) {
    tracePaint(renderer, descriptor, paintTarget, {
      val composeInfo = info()
      val swingInfo = SwingPaintInfo(composeInfo.hostClass, composeInfo.x.roundToInt(), composeInfo.y.roundToInt(),
                                     composeInfo.density, composeInfo.scale, composeInfo.finalWidth, composeInfo.finalHeight)
      swingInfo to composeInfo
    }, paint)
  }

  private fun tracePaint(
    renderer: Any,
    descriptor: IconDescriptor,
    paintTarget: Any?,
    info: () -> Pair<SwingPaintInfo, ComposePaintInfo?>,
    paint: () -> Unit,
  ) {
    val state = threadState.get()
    if (state.inCallback || state.stack != null || captures.isEmpty()) {
      // nested paint (e.g. a Swing icon layer) is already part of the current capture's tree
      paint()
      return
    }
    val accepting = callback(state, emptyList()) { captures.filter { capture -> capture.accepts(paintTarget) } }
    if (accepting.isEmpty()) {
      paint()
      return
    }
    val paintInfo = callback(state, null) { info() }
    if (paintInfo == null) {
      paint()
      return
    }
    val root = NodeBuilder(renderer, descriptor, null, null, 0, 0)
    state.stack = arrayListOf(root)
    try {
      paint()
    }
    finally {
      state.stack = null
    }
    val result = RenderCapture(paintTarget, descriptor, renderer, paintInfo.first, root.children.map { it.build() },
                               Thread.currentThread().name, System.nanoTime(), paintInfo.second)
    callback(state, Unit) {
      for (capture in accepting) {
        try {
          capture.listener(result)
        }
        catch (e: Throwable) {
          logger.log(System.Logger.Level.WARNING, "Icon render capture listener failed", e)
        }
      }
    }
  }

  // ---- redraw requests ----

  /**
   * Registers a painter that can force its icons to be drawn again, for paint systems that cache drawings and therefore are
   * not re-run by a plain repaint of the hosting component (Compose). [listener] gets the paint target passed to
   * [requestRedraw] (`null` = everything) and is called on the thread calling [requestRedraw].
   */
  fun addRedrawListener(listener: (paintTarget: Any?) -> Unit): AutoCloseable {
    redrawListeners.add(listener)
    return AutoCloseable { redrawListeners.remove(listener) }
  }

  /**
   * Asks all registered painters (see [addRedrawListener]) to draw the icons painted into [paintTarget] (`null` = all) again,
   * so that an open [capture] receives them. Call on the UI thread of the painted components (the EDT in the IDE).
   */
  fun requestRedraw(paintTarget: Any?) {
    for (listener in redrawListeners) {
      try {
        listener(paintTarget)
      }
      catch (e: Throwable) {
        logger.log(System.Logger.Level.WARNING, "Icon redraw listener failed", e)
      }
    }
  }

  /** Brackets one render of an icon renderer: records it into an open capture and reports a hit if it is flagged. */
  @JvmStatic
  fun traceRender(
    renderer: IconRenderer,
    paintingContext: LayerPaintingContext,
    dimensions: Dimensions?,
    render: () -> Unit,
  ) {
    trace(renderer, renderer.iconDescriptor, paintingContext, dimensions, render)
  }

  /** Brackets one render of a layer renderer; [layer] is what it renders, if known. */
  @JvmStatic
  fun traceLayer(renderer: IconLayerRenderer, layer: IconLayer?, paintingContext: LayerPaintingContext) {
    val dimensions = runCatching { renderer.layout.consumedSpace(paintingContext.scaling) }.getOrNull()
    trace(renderer, layer, paintingContext, dimensions) { renderer.render(paintingContext) }
  }

  private fun trace(renderer: Any, subject: Any?, paintingContext: LayerPaintingContext, dimensions: Dimensions?, render: () -> Unit) {
    val state = threadState.get()
    if (state.inCallback) {
      render()
      return
    }
    val stack = state.stack
    var node: NodeBuilder? = null
    try {
      if (stack != null) {
        node = NodeBuilder(renderer, subject, paintingContext.scaling, dimensions, paintingContext.offsetX, paintingContext.offsetY)
        stack.last().children.add(node)
        stack.add(node)
      }
      val counter = synchronized(lock) { flags.get(renderer) }
      if (counter != null) {
        counter.incrementAndGet()
        val hit = IconRenderHit(renderer, subject, paintingContext.scaling, dimensions, paintingContext.offsetX,
                                paintingContext.offsetY, Thread.currentThread().name, System.nanoTime())
        callback(state, Unit) { flaggedIconRendered(hit) }
      }
    }
    catch (e: Throwable) {
      // never break rendering because of the debugger
      logger.log(System.Logger.Level.WARNING, "Icon render debugger failed", e)
    }
    try {
      render()
    }
    finally {
      if (node != null && stack != null && stack.lastOrNull() === node) {
        stack.removeAt(stack.size - 1)
      }
    }
  }

  /**
   * The debugger stop point: called on the rendering thread, with the full render stack below it, whenever a flagged
   * renderer instance renders. To stop there, either
   * - add a Java Exception Breakpoint on `com.intellij.platform.icons.impl.rendering.debug.IconRenderBreakpoint` with
   *   "Caught exception" enabled (persistent, needs no code navigation; the exception carries [hit]), or
   * - put a line breakpoint into this method (inspect [hit]).
   *
   * Deliberately a separate, non-inline method that throws and swallows [IconRenderBreakpoint]; the exception never
   * escapes. It only runs for flagged renderers, so the cost of creating the exception does not matter.
   */
  @JvmStatic
  fun flaggedIconRendered(hit: IconRenderHit) {
    try {
      throw IconRenderBreakpoint(hit)
    }
    catch (@Suppress("unused") breakpoint: IconRenderBreakpoint) {
      // intentionally swallowed: the throw only exists as a target for an exception breakpoint
    }
    for (listener in hitListeners) {
      try {
        listener(hit)
      }
      catch (e: Throwable) {
        logger.log(System.Logger.Level.WARNING, "Icon render hit listener failed", e)
      }
    }
  }

  /**
   * Runs [block] without tracing on this thread: renders inside it are neither captured nor reported as hits of flagged
   * renderers. For tooling that paints icons itself (e.g. the Icon Inspector's previews), so its own painting never stops
   * the debugger. Nests safely.
   */
  fun <T> untraced(block: () -> T): T {
    val state = threadState.get()
    val previous = state.inCallback
    state.inCallback = true
    try {
      return block()
    }
    finally {
      state.inCallback = previous
    }
  }

  private inline fun <T> callback(state: ThreadState, fallback: T, block: () -> T): T {
    state.inCallback = true
    try {
      return block()
    }
    catch (e: Throwable) {
      logger.log(System.Logger.Level.WARNING, "Icon render debugger callback failed", e)
      return fallback
    }
    finally {
      state.inCallback = false
    }
  }

  private class ThreadState {
    var inCallback: Boolean = false

    /** Non-null while a capture is being recorded on this thread; the last element is the innermost open node. */
    var stack: ArrayList<NodeBuilder>? = null
  }

  private class Capture(private val filter: (Any?) -> Boolean, val listener: (RenderCapture) -> Unit) {
    fun accepts(paintTarget: Any?): Boolean = try {
      filter(paintTarget)
    }
    catch (e: Throwable) {
      logger.log(System.Logger.Level.WARNING, "Icon render capture filter failed", e)
      false
    }
  }

  private class NodeBuilder(
    val renderer: Any,
    val subject: Any?,
    val scaling: ScalingContext?,
    val dimensions: Dimensions?,
    val offsetX: Int,
    val offsetY: Int,
  ) {
    val children = ArrayList<NodeBuilder>()

    fun build(): RenderNode =
      RenderNode(renderer, subject, scaling!!, dimensions, offsetX, offsetY, children.map { it.build() })
  }
}
