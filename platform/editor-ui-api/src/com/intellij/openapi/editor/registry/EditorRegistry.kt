// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.registry

import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

/**
 * Immutable values of the editor registry keys.
 * Get the current snapshot with [current].
 */
@ApiStatus.Internal
class EditorRegistry private constructor(
  private val rightMarginShownInReadOnlyFiles: Boolean,
  private val rightMarginAdjusted: Boolean,
  private val foldingHighlighted: Boolean,
  private val oldFullHorizontalSelection: Boolean,
  private val caretLineAtCustomFold: Boolean,
  private val markupCacheDebug: Boolean,
  private val focusModeColorDark: Color,
  private val focusModeColorLight: Color,

  private val zeroLatencyRendering: Boolean,
  private val zeroLatencyDoubleBuffering: Boolean,
  private val zeroLatencyPipelineFlush: Boolean,
  private val zeroLatencyDebug: Boolean,

  private val gutterFreeMarkersUpdatedOnHover: Boolean,
  private val distractionFreeGutterSeparator: Boolean,
  private val gutterAreaDebug: Boolean,
  private val gutterIconsScaled: Boolean,
  private val newUiNormalizedLineHeight: Double,
  private val popupProgressIconDelayMs: Int,

  private val dragSensitivity: Int,
  private val dragWithRightButtonDisabled: Boolean,
  private val mouseSelectionResetTimeoutMs: Int,
  private val mouseSelectionResetDeadZone: Int,
  private val cursorHiddenWhenTyping: Boolean,
  private val inputMethodInlay: Boolean,
  private val macPressAndHoldWorkaround: Boolean,
  private val macPressAndHoldBruteWorkaround: Boolean,
  private val debuggerClickDisablesBreakpoints: Boolean,

  private val vimLikeBlockCaretSelection: Boolean,
  private val maxCaretCount: Int,
  private val primarySelectionUpdate: Boolean,
  private val smoothCaretDurationMs: Int,
  private val caretWidth: Int,

  private val scrollingAnimationIntervalMs: Int,
  private val navigationScrollDurationMs: Int,
  private val viewportWidthUpdateFrequency: Int,
  private val transparentScrollBar: Boolean,
  private val dynamicScrollBars: Boolean,
  private val fullWidthScrollBar: Boolean,
  private val xcodeVerticalSpacing: Boolean,
  private val verticalSpacingCorrectRounding: Boolean,

  private val animationCache: Boolean,
  private val animationCacheDebug: Boolean,
  private val virtualLines: Int,
  private val rtlDisabled: Boolean,

  private val stickyLinesShadow: Boolean,
  private val stickyLinesShadowDebug: Boolean,
  private val distractionFreeMode: Boolean,
  private val localEditorUx: Boolean,
) {
  fun isRightMarginShownInReadOnlyFiles(): Boolean = rightMarginShownInReadOnlyFiles
  fun isRightMarginAdjusted(): Boolean = rightMarginAdjusted
  fun isFoldingHighlighted(): Boolean = foldingHighlighted
  fun isOldFullHorizontalSelectionEnabled(): Boolean = oldFullHorizontalSelection
  fun isCaretLineHighlightedAtCustomFold(): Boolean = caretLineAtCustomFold
  fun isMarkupCacheDebugEnabled(): Boolean = markupCacheDebug
  fun focusModeColorDark(): Color = focusModeColorDark
  fun focusModeColorLight(): Color = focusModeColorLight

  fun isZeroLatencyRenderingEnabled(): Boolean = zeroLatencyRendering
  fun isZeroLatencyDoubleBufferingEnabled(): Boolean = zeroLatencyDoubleBuffering
  fun isZeroLatencyPipelineFlushEnabled(): Boolean = zeroLatencyPipelineFlush
  fun isZeroLatencyDebugEnabled(): Boolean = zeroLatencyDebug

  fun isGutterFreeMarkersUpdatedOnHover(): Boolean = gutterFreeMarkersUpdatedOnHover
  fun isDistractionFreeGutterSeparatorShown(): Boolean = distractionFreeGutterSeparator
  fun isGutterAreaDebugEnabled(): Boolean = gutterAreaDebug
  fun isGutterIconScaled(): Boolean = gutterIconsScaled
  fun newUiNormalizedLineHeight(): Double = newUiNormalizedLineHeight
  fun popupProgressIconDelayMs(): Int = popupProgressIconDelayMs

  fun dragSensitivity(): Int = dragSensitivity
  fun isDragWithRightButtonDisabled(): Boolean = dragWithRightButtonDisabled
  fun mouseSelectionResetTimeoutMs(): Int = mouseSelectionResetTimeoutMs
  fun mouseSelectionResetDeadZone(): Int = mouseSelectionResetDeadZone
  fun isCursorHiddenWhenTyping(): Boolean = cursorHiddenWhenTyping
  fun isInputMethodInlayEnabled(): Boolean = inputMethodInlay
  fun isMacPressAndHoldWorkaroundEnabled(): Boolean = macPressAndHoldWorkaround
  fun isMacPressAndHoldBruteWorkaroundEnabled(): Boolean = macPressAndHoldBruteWorkaround
  fun isDebuggerClickDisablingBreakpoints(): Boolean = debuggerClickDisablesBreakpoints

  fun isVimLikeBlockCaretSelection(): Boolean = vimLikeBlockCaretSelection
  fun maxCaretCount(): Int = maxCaretCount
  fun isPrimarySelectionUpdateEnabled(): Boolean = primarySelectionUpdate
  fun smoothCaretDurationMs(): Int = smoothCaretDurationMs
  fun caretWidth(): Int = caretWidth

  fun scrollingAnimationIntervalMs(): Int = scrollingAnimationIntervalMs
  fun navigationScrollDurationMs(): Int = navigationScrollDurationMs
  fun viewportWidthUpdateFrequency(): Int = viewportWidthUpdateFrequency
  fun isScrollBarTransparent(): Boolean = transparentScrollBar
  fun isDynamicScrollBarsEnabled(): Boolean = dynamicScrollBars
  fun isScrollBarFullWidth(): Boolean = fullWidthScrollBar
  fun isXcodeVerticalSpacing(): Boolean = xcodeVerticalSpacing
  fun isVerticalSpacingRoundingCorrect(): Boolean = verticalSpacingCorrectRounding

  fun isAnimationCacheEnabled(): Boolean = animationCache
  fun isAnimationCacheDebugEnabled(): Boolean = animationCacheDebug
  fun virtualLines(): Int = virtualLines
  fun isRtlDisabled(): Boolean = rtlDisabled

  fun isStickyLinesShadowShown(): Boolean = stickyLinesShadow
  fun isStickyLinesShadowDebugEnabled(): Boolean = stickyLinesShadowDebug
  fun isDistractionFreeMode(): Boolean = distractionFreeMode
  fun isLocalEditorUxEnabled(): Boolean = localEditorUx

  companion object {
    /**
     * Returns the current snapshot, or [DEFAULT] until the service exists.
     */
    @JvmStatic
    fun current(): EditorRegistry {
      return EditorRegistryService.getInstance().snapshot()
    }

    @Suppress("UseJBColor")
    internal val DEFAULT: EditorRegistry = EditorRegistry(
      rightMarginShownInReadOnlyFiles = true,
      rightMarginAdjusted = false,
      foldingHighlighted = true,
      oldFullHorizontalSelection = false,
      caretLineAtCustomFold = false,
      markupCacheDebug = false,
      focusModeColorDark = Color(0x404040),
      focusModeColorLight = Color(0xC0C0C0),

      zeroLatencyRendering = true,
      zeroLatencyDoubleBuffering = true,
      zeroLatencyPipelineFlush = true,
      zeroLatencyDebug = false,

      gutterFreeMarkersUpdatedOnHover = true,
      distractionFreeGutterSeparator = false,
      gutterAreaDebug = false,
      gutterIconsScaled = true,
      newUiNormalizedLineHeight = 18.0,
      popupProgressIconDelayMs = 500,

      dragSensitivity = 5,
      dragWithRightButtonDisabled = false,
      mouseSelectionResetTimeoutMs = 1000,
      mouseSelectionResetDeadZone = 4,
      cursorHiddenWhenTyping = true,
      inputMethodInlay = false,
      macPressAndHoldWorkaround = true,
      macPressAndHoldBruteWorkaround = false,
      debuggerClickDisablesBreakpoints = false,

      vimLikeBlockCaretSelection = false,
      maxCaretCount = 1000,
      primarySelectionUpdate = true,
      smoothCaretDurationMs = 120,
      caretWidth = 2,

      scrollingAnimationIntervalMs = 100,
      navigationScrollDurationMs = 100,
      viewportWidthUpdateFrequency = 200,
      transparentScrollBar = true,
      dynamicScrollBars = false,
      fullWidthScrollBar = false,
      xcodeVerticalSpacing = false,
      verticalSpacingCorrectRounding = false,

      animationCache = true,
      animationCacheDebug = false,
      virtualLines = 5,
      rtlDisabled = false,

      stickyLinesShadow = true,
      stickyLinesShadowDebug = false,
      distractionFreeMode = false,
      localEditorUx = true,
    )

    /**
     * Calls [onKey] for each key before it reads the key.
     */
    internal fun read(onKey: (String) -> Unit): EditorRegistry {
      val r = EditorRegistryReader(onKey)
      val rightMarginShownInReadOnlyFiles = r.`is`("editor.show.right.margin.in.read.only.files", DEFAULT.rightMarginShownInReadOnlyFiles)
      val rightMarginAdjusted = r.`is`("editor.adjust.right.margin", DEFAULT.rightMarginAdjusted)
      val foldingHighlighted = r.`is`("editor.highlight.foldings", DEFAULT.foldingHighlighted)
      val oldFullHorizontalSelection = r.`is`("editor.old.full.horizontal.selection.enabled", DEFAULT.oldFullHorizontalSelection)
      val caretLineAtCustomFold = r.`is`("highlight.caret.line.at.custom.fold", DEFAULT.caretLineAtCustomFold)
      val markupCacheDebug = r.`is`("cache.markup.debug", DEFAULT.markupCacheDebug)
      val focusModeColorDark = r.color("editor.focus.mode.color.dark", DEFAULT.focusModeColorDark)
      val focusModeColorLight = r.color("editor.focus.mode.color.light", DEFAULT.focusModeColorLight)

      val zeroLatencyRendering = r.`is`("editor.zero.latency.rendering", DEFAULT.zeroLatencyRendering)
      val zeroLatencyDoubleBuffering = r.`is`("editor.zero.latency.rendering.double.buffering", DEFAULT.zeroLatencyDoubleBuffering)
      val zeroLatencyPipelineFlush = r.`is`("editor.zero.latency.rendering.pipeline.flush", DEFAULT.zeroLatencyPipelineFlush)
      val zeroLatencyDebug = r.`is`("editor.zero.latency.rendering.debug", DEFAULT.zeroLatencyDebug)

      val gutterFreeMarkersUpdatedOnHover = r.`is`("ide.gutter.update.free.markers.on.hover", DEFAULT.gutterFreeMarkersUpdatedOnHover)
      val distractionFreeGutterSeparator = r.`is`("editor.distraction.gutter.separator", DEFAULT.distractionFreeGutterSeparator)
      val gutterAreaDebug = r.`is`("ide.debug.gutter.area", DEFAULT.gutterAreaDebug)
      val gutterIconsScaled = r.`is`("editor.scale.gutter.icons", DEFAULT.gutterIconsScaled)
      val newUiNormalizedLineHeight = r.doubleValue("ide.new.ui.editor.normalized.line.height", DEFAULT.newUiNormalizedLineHeight)
      val popupProgressIconDelayMs = r.intValue("actionSystem.popup.progress.icon.delay", DEFAULT.popupProgressIconDelayMs)

      val dragSensitivity = r.intValue("editor.drag.sensitivity", DEFAULT.dragSensitivity).coerceIn(0, 25)
      val dragWithRightButtonDisabled = r.`is`("editor.disable.drag.with.right.button", DEFAULT.dragWithRightButtonDisabled)
      val mouseSelectionResetTimeoutMs = r.intValue("editor.mouseSelectionStateResetTimeout", DEFAULT.mouseSelectionResetTimeoutMs)
      val mouseSelectionResetDeadZone = r.intValue("editor.mouseSelectionStateResetDeadZone", DEFAULT.mouseSelectionResetDeadZone)
      val cursorHiddenWhenTyping = r.`is`("ide.hide.cursor.when.typing", DEFAULT.cursorHiddenWhenTyping)
      val inputMethodInlay = r.`is`("editor.input.method.inlay", DEFAULT.inputMethodInlay)
      val macPressAndHoldWorkaround = r.`is`("ide.mac.pressAndHold.workaround", DEFAULT.macPressAndHoldWorkaround)
      val macPressAndHoldBruteWorkaround = r.`is`("ide.mac.pressAndHold.brute.workaround", DEFAULT.macPressAndHoldBruteWorkaround)
      val debuggerClickDisablesBreakpoints = r.`is`("debugger.click.disable.breakpoints", DEFAULT.debuggerClickDisablesBreakpoints)

      val vimLikeBlockCaretSelection = r.`is`("editor.block.caret.selection.vim-like", DEFAULT.vimLikeBlockCaretSelection)
      val maxCaretCount = r.intValue("editor.max.caret.count", DEFAULT.maxCaretCount)
      val primarySelectionUpdate = r.`is`("editor.caret.update.primary.selection", DEFAULT.primarySelectionUpdate)
      val smoothCaretDurationMs = r.intValue("editor.smooth.caret.duration", DEFAULT.smoothCaretDurationMs)
      val caretWidth = r.intValue("editor.caret.width", DEFAULT.caretWidth)

      val scrollingAnimationIntervalMs = r.intValue("editor.scrolling.animation.interval.ms", DEFAULT.scrollingAnimationIntervalMs)
      val navigationScrollDurationMs = r.intValue("idea.editor.smooth.scrolling.navigation.duration", DEFAULT.navigationScrollDurationMs)
      val viewportWidthUpdateFrequency = r.intValue("editor.viewport.width.update.frequency", DEFAULT.viewportWidthUpdateFrequency)
      val transparentScrollBar = r.`is`("editor.transparent.scrollbar", DEFAULT.transparentScrollBar)
      val dynamicScrollBars = r.`is`("idea.true.smooth.scrolling.dynamic.scrollbars", DEFAULT.dynamicScrollBars)
      val fullWidthScrollBar = r.`is`("editor.full.width.scrollbar", DEFAULT.fullWidthScrollBar)
      val xcodeVerticalSpacing = r.`is`("editor.text.xcode.vertical.spacing", DEFAULT.xcodeVerticalSpacing)
      val verticalSpacingCorrectRounding = r.`is`("editor.text.vertical.spacing.correct.rounding", DEFAULT.verticalSpacingCorrectRounding)

      val animationCache = r.`is`("editor.animation.cache.enabled", DEFAULT.animationCache)
      val animationCacheDebug = r.`is`("editor.animation.cache.debug.enabled", DEFAULT.animationCacheDebug)
      val virtualLines = r.intValue("editor.virtual.lines", DEFAULT.virtualLines)
      val rtlDisabled = r.`is`("editor.disable.rtl", DEFAULT.rtlDisabled)

      val stickyLinesShadow = r.`is`("editor.show.sticky.lines.shadow", DEFAULT.stickyLinesShadow)
      val stickyLinesShadowDebug = r.`is`("editor.show.sticky.lines.shadow.debug", DEFAULT.stickyLinesShadowDebug)
      val distractionFreeMode = r.`is`("editor.distraction.free.mode", DEFAULT.distractionFreeMode)
      val localEditorUx = r.`is`("editor.rd.local.ux", DEFAULT.localEditorUx)

      return EditorRegistry(
        rightMarginShownInReadOnlyFiles = rightMarginShownInReadOnlyFiles,
        rightMarginAdjusted = rightMarginAdjusted,
        foldingHighlighted = foldingHighlighted,
        oldFullHorizontalSelection = oldFullHorizontalSelection,
        caretLineAtCustomFold = caretLineAtCustomFold,
        markupCacheDebug = markupCacheDebug,
        focusModeColorDark = focusModeColorDark,
        focusModeColorLight = focusModeColorLight,

        zeroLatencyRendering = zeroLatencyRendering,
        zeroLatencyDoubleBuffering = zeroLatencyDoubleBuffering,
        zeroLatencyPipelineFlush = zeroLatencyPipelineFlush,
        zeroLatencyDebug = zeroLatencyDebug,

        gutterFreeMarkersUpdatedOnHover = gutterFreeMarkersUpdatedOnHover,
        distractionFreeGutterSeparator = distractionFreeGutterSeparator,
        gutterAreaDebug = gutterAreaDebug,
        gutterIconsScaled = gutterIconsScaled,
        newUiNormalizedLineHeight = newUiNormalizedLineHeight,
        popupProgressIconDelayMs = popupProgressIconDelayMs,

        dragSensitivity = dragSensitivity,
        dragWithRightButtonDisabled = dragWithRightButtonDisabled,
        mouseSelectionResetTimeoutMs = mouseSelectionResetTimeoutMs,
        mouseSelectionResetDeadZone = mouseSelectionResetDeadZone,
        cursorHiddenWhenTyping = cursorHiddenWhenTyping,
        inputMethodInlay = inputMethodInlay,
        macPressAndHoldWorkaround = macPressAndHoldWorkaround,
        macPressAndHoldBruteWorkaround = macPressAndHoldBruteWorkaround,
        debuggerClickDisablesBreakpoints = debuggerClickDisablesBreakpoints,

        vimLikeBlockCaretSelection = vimLikeBlockCaretSelection,
        maxCaretCount = maxCaretCount,
        primarySelectionUpdate = primarySelectionUpdate,
        smoothCaretDurationMs = smoothCaretDurationMs,
        caretWidth = caretWidth,

        scrollingAnimationIntervalMs = scrollingAnimationIntervalMs,
        navigationScrollDurationMs = navigationScrollDurationMs,
        viewportWidthUpdateFrequency = viewportWidthUpdateFrequency,
        transparentScrollBar = transparentScrollBar,
        dynamicScrollBars = dynamicScrollBars,
        fullWidthScrollBar = fullWidthScrollBar,
        xcodeVerticalSpacing = xcodeVerticalSpacing,
        verticalSpacingCorrectRounding = verticalSpacingCorrectRounding,

        animationCache = animationCache,
        animationCacheDebug = animationCacheDebug,
        virtualLines = virtualLines,
        rtlDisabled = rtlDisabled,

        stickyLinesShadow = stickyLinesShadow,
        stickyLinesShadowDebug = stickyLinesShadowDebug,
        distractionFreeMode = distractionFreeMode,
        localEditorUx = localEditorUx,
      )
    }
  }
}

private class EditorRegistryReader(private val onKey: (String) -> Unit) {
  private val registry = RegistryManager.getInstance()

  fun `is`(key: String, defaultValue: Boolean): Boolean {
    onKey(key)
    return registry.`is`(key, defaultValue)
  }

  fun intValue(key: String, defaultValue: Int): Int {
    onKey(key)
    return registry.intValue(key, defaultValue)
  }

  fun doubleValue(key: String, defaultValue: Double): Double {
    onKey(key)
    return registry.stringValue(key, defaultValue.toString()).toDoubleOrNull() ?: defaultValue
  }

  fun color(key: String, defaultValue: Color): Color {
    onKey(key)
    return registry.get(key).asColor(defaultValue) ?: defaultValue
  }
}
