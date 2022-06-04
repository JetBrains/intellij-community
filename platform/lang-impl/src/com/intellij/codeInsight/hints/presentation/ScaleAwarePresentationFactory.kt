// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextAware
import com.intellij.util.IconUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon
import kotlin.math.roundToInt
import kotlin.reflect.KProperty

/**
 * Presentation factory, which handles presentation mode and scales Icons and Insets
 */
@ApiStatus.Experimental
class ScaleAwarePresentationFactory(
  val editor: Editor,
  private val delegate: PresentationFactory
) : InlayPresentationFactory by delegate {
  fun lineCentered(presentation: InlayPresentation): InlayPresentation {
    return LineCenteredInset(presentation, editor)
  }

  override fun container(presentation: InlayPresentation,
                         padding: InlayPresentationFactory.Padding?,
                         roundedCorners: InlayPresentationFactory.RoundedCorners?,
                         background: Color?,
                         backgroundAlpha: Float): InlayPresentation {
    return ScaledContainerPresentation(presentation, editor, padding, roundedCorners, background, backgroundAlpha)
  }

  override fun icon(icon: Icon): InlayPresentation {
    return MyScaledIconPresentation(icon, editor, fontShift = 0)
  }

  fun icon(icon: Icon, debugName: String, fontShift: Int): InlayPresentation {
    return MyScaledIconPresentation(icon, editor, debugName, fontShift)
  }

  fun inset(base: InlayPresentation, left: Int = 0, right: Int = 0, top: Int = 0, down: Int = 0): InlayPresentation {
    return ScaledInsetPresentation(
      base,
      left = left,
      right = right,
      top = top,
      down = down,
      editor = editor
    )
  }

  fun seq(vararg presentations: InlayPresentation): InlayPresentation {
    return delegate.seq(*presentations)
  }
}

private class ScaledInsetPresentation(
  private val presentation: InlayPresentation,
  private val left: Int,
  private val right: Int,
  private val top: Int,
  private val down: Int,
  private val editor: Editor
) : ScaledDelegatedPresentation() {
  override val delegate: InsetPresentation by valueOf<InsetPresentation, Float> { fontSize ->
    InsetPresentation(
      presentation,
      scaleByFont(left, fontSize),
      scaleByFont(right, fontSize),
      scaleByFont(top, fontSize),
      scaleByFont(down, fontSize)
    )
  }.withState {
    editor.colorsScheme.editorFontSize2D
  }
}

private class ScaledContainerPresentation(
  private val presentation: InlayPresentation,
  private val editor: Editor,
  private val padding: InlayPresentationFactory.Padding? = null,
  private val roundedCorners: InlayPresentationFactory.RoundedCorners? = null,
  private val background: Color? = null,
  private val backgroundAlpha: Float = 0.55f
) : ScaledDelegatedPresentation() {
  override val delegate: ContainerInlayPresentation by valueOf<ContainerInlayPresentation, Float> { fontSize ->
    ContainerInlayPresentation(
      presentation,
      scaleByFont(padding, fontSize),
      scaleByFont(roundedCorners, fontSize),
      background,
      backgroundAlpha
    )
  }.withState {
    editor.colorsScheme.editorFontSize2D
  }
}

private abstract class ScaledDelegatedPresentation : BasePresentation() {
  protected abstract val delegate: InlayPresentation

  override val width: Int
    get() = delegate.width

  override val height: Int
    get() = delegate.height

  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    delegate.paint(g, attributes)
  }

  override fun toString(): String {
    return delegate.toString()
  }

  override fun mouseClicked(event: MouseEvent, translated: Point) {
    delegate.mouseExited()
  }

  override fun mouseMoved(event: MouseEvent, translated: Point) {
    delegate.mouseMoved(event, translated)
  }

  override fun mouseExited() {
    delegate.mouseExited()
  }
}

private class LineCenteredInset(
  private val presentation: InlayPresentation,
  private val editor: Editor
) : ScaledDelegatedPresentation() {
  override val delegate: InlayPresentation by valueOf<InlayPresentation, Int> { lineHeight ->
    val innerHeight = presentation.height
    InsetPresentation(presentation, top = (lineHeight - innerHeight) / 2)
  }.withState {
    editor.lineHeight
  }
}

private class MyScaledIconPresentation(val icon: Icon,
                                       val editor: Editor,
                                       private val debugName: String = "image",
                                       val fontShift: Int) : BasePresentation() {
  override val width: Int
    get() = scaledIcon.iconWidth
  override val height: Int
    get() = scaledIcon.iconHeight

  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    val graphics = g.create() as Graphics2D
    graphics.composite = AlphaComposite.SrcAtop.derive(1.0f)
    scaledIcon.paintIcon(editor.component, graphics, 0, 0)
    graphics.dispose()
  }

  override fun toString(): String = "<$debugName>"

  private val scaledIcon by valueOf<Icon, Float> { fontSize ->
    (icon as? ScaleContextAware)?.updateScaleContext(ScaleContext.create(editor.component))
    IconUtil.scaleByFont(icon, editor.component, fontSize)
  }.withState {
    editor.colorsScheme.editorFontSize2D - fontShift
  }
}

private class StateDependantValue<TData : Any, TState : Any>(
  private val valueProvider: (TState) -> TData,
  private val stateProvider: () -> TState
) {
  private var currentState: TState? = null
  private lateinit var cachedValue: TData

  operator fun getValue(thisRef: Any?, property: KProperty<*>): TData {
    val state = stateProvider()
    if (state != currentState) {
      currentState = state
      cachedValue = valueProvider(state)
    }
    return cachedValue
  }
}

private fun <TData : Any, TState : Any> valueOf(dataProvider: (TState) -> TData): StateDependantValueBuilder<TData, TState> {
  return StateDependantValueBuilder(dataProvider)
}

private class StateDependantValueBuilder<TData : Any, TState : Any>(private val dataProvider: (TState) -> TData) {
  fun withState(stateProvider: () -> TState): StateDependantValue<TData, TState> {
    return StateDependantValue(dataProvider, stateProvider)
  }
}

private fun scaleByFont(sizeFor12: Int, fontSize: Float) = (JBUIScale.getFontScale(fontSize) * sizeFor12).roundToInt()

private fun scaleByFont(paddingFor12: InlayPresentationFactory.Padding?, fontSize: Float) =
  paddingFor12?.let { (left, right, top, bottom) ->
    InlayPresentationFactory.Padding(
      left = scaleByFont(left, fontSize),
      right = scaleByFont(right, fontSize),
      top = scaleByFont(top, fontSize),
      bottom = scaleByFont(bottom, fontSize)
    )
  }

private fun scaleByFont(roundedCornersFor12: InlayPresentationFactory.RoundedCorners?, fontSize: Float) =
  roundedCornersFor12?.let { (arcWidth, arcHeight) ->
    InlayPresentationFactory.RoundedCorners(
      arcWidth = scaleByFont(arcWidth, fontSize),
      arcHeight = scaleByFont(arcHeight, fontSize)
    )
  }