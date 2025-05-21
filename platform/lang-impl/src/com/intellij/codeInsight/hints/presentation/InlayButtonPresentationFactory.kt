// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.presentation

import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.InlayButtonPresentationFactory.InlayButtonPresentationBuilder.InlayButtonPresentation
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors.INLAY_BUTTON_HINT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor
import java.util.EnumSet
import javax.swing.Icon

@ApiStatus.Internal
open class InlayButtonPresentationFactory(
  private val editor: Editor,
  private val delegate: PresentationFactory,
  private val defaultAttributesKey: TextAttributesKey = DefaultLanguageHighlighterColors.INLAY_BUTTON_DEFAULT,
  private val hoveredAttributesKey: TextAttributesKey = DefaultLanguageHighlighterColors.INLAY_BUTTON_HOVERED,
  private val focusedAttributesKey: TextAttributesKey = DefaultLanguageHighlighterColors.INLAY_BUTTON_FOCUSED,
) {
  companion object {
    const val DEFAULT_CORNER_RADIUS: Int = 10
    private const val DROPDOWN_RIGHT_CORNER_RADIUS = 6
    private const val CLOSE_RIGHT_CORNER_RADIUS = 4
    private const val ICON_TEXT_LEFT_PADDING = 4
    private const val HINT_LEFT_PADDING = 8
    private const val CLOSE_LEFT_PADDING = 8
    private const val ADDITIONAL_SPACING_BETWEEN_COMPONENTS = 2
    private const val DEFAULT_BORDER_WIDTH = 1

    val onePixelBorderProvider: InsetValueProvider = object : InsetValueProvider {
      override val top: Int
        get() = 1
      override val down: Int
        get() = 1
      override val left: Int
        get() = 1
      override val right: Int
        get() = 1
    }
  }

  private val textMetricsStorage = InlayHintsUtils.getTextMetricStorage(editor)

  fun icon(icon: Icon): InlayButtonPresentationBuilder =
    createBuilder(ScaleAwareIconPresentation(icon = icon, editor = editor, fontShift = 0))

  fun text(text: String): InlayButtonPresentationBuilder =
    createBuilder(textWithoutPadding(text, isSmall = false))

  fun smallText(text: String): InlayButtonPresentationBuilder =
    createBuilder(textWithoutPadding(text, isSmall = true))

  fun scaledIcon(icon: Icon, scaleFactor: Float): InlayButtonPresentationBuilder =
    createBuilder(delegate.scaledIcon(icon, scaleFactor))

  fun smallScaledIcon(icon: Icon): InlayButtonPresentationBuilder =
    createBuilder(delegate.smallScaledIcon(icon))

  private fun seq(vararg presentations: InlayPresentation): InlayPresentation =
    delegate.seq(*presentations)

  fun seq(vararg presentationBuilders: InlayButtonPresentationBuilder): InlayPresentation =
    delegate.seq(*presentationBuilders.map { it.build() }.toTypedArray())

  fun iconAndText(icon: Icon, text: String): InlayButtonPresentationBuilder =
    InlayButtonPresentationBuilder(
      this,
      seq(
        icon(icon).get(),
        createPaddedPresentation(smallText(text).get(), left = ICON_TEXT_LEFT_PADDING)
      )
    )

  private fun createBuilder(basePresentation: InlayPresentation): InlayButtonPresentationBuilder =
    InlayButtonPresentationBuilder(this, createPaddedPresentation(basePresentation))

  private fun createPaddedPresentation(
    base: InlayPresentation,
    left: Int = 0,
  ): InlayButtonPresentation {
    return InlayButtonPresentation(
      DynamicInsetPresentation(base, CenteredInsetValueProvider(base, textMetricsStorage, left))
    )
  }

  private fun textWithoutPadding(text: String, isSmall: Boolean): InlayPresentation {
    return DynamicInsetPresentation(
      TextInlayPresentation(textMetricsStorage, isSmall, text), TextInsetValueProvider(textMetricsStorage, isSmall)
    )
  }

  class InlayButtonPresentationBuilder internal constructor(
    private val factory: InlayButtonPresentationFactory,
    private var presentation: InlayPresentation,
    private var rightCornerRadius: Int = DEFAULT_CORNER_RADIUS,
    private var clickListener: InlayPresentationFactory.ClickListener? = null,
  ) {
    internal fun get(): InlayPresentation = presentation

    fun hint(hint: @NlsContexts.HintText String): InlayButtonPresentationBuilder {
      presentation = factory.seq(
        presentation,
        factory.createPaddedPresentation(
          withInlayAttributes(factory.smallText(hint).get(), INLAY_BUTTON_HINT),
          left = HINT_LEFT_PADDING
        )
      )
      return this
    }

    fun dropdown(): InlayButtonPresentationBuilder {
      presentation = factory.seq(
        presentation,
        factory.createPaddedPresentation(factory.icon(AllIcons.General.LinkDropTriangle).get())
      )
      rightCornerRadius = DROPDOWN_RIGHT_CORNER_RADIUS
      return this
    }

    fun close(): InlayButtonPresentationBuilder {
      presentation = factory.seq(
        presentation,
        factory.createPaddedPresentation(factory.icon(AllIcons.Windows.Close).get(), left = CLOSE_LEFT_PADDING)
      )
      rightCornerRadius = CLOSE_RIGHT_CORNER_RADIUS
      return this
    }

    fun withTooltip(tooltip: @NlsContexts.Tooltip String): InlayButtonPresentationBuilder {
      presentation = factory.delegate.withTooltip(tooltip, presentation)
      return this
    }

    fun onClick(clickListener: InlayPresentationFactory.ClickListener): InlayButtonPresentationBuilder {
      this.clickListener = clickListener
      return this
    }

    fun build(): InlayPresentation = factory.createPaddedPresentation(
      factory.delegate.withCursorOnHover(
        withInlayAttributes(border(), factory.defaultAttributesKey), Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      ),
      left = ADDITIONAL_SPACING_BETWEEN_COMPONENTS
    )

    fun buildFocused(): InlayPresentation = factory.delegate.withCursorOnHover(
      withInlayAttributes(border(2), factory.focusedAttributesKey), Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    )

    fun buildHovered(): InlayPresentation = factory.delegate.withCursorOnHover(
      withInlayAttributes(border(), factory.hoveredAttributesKey), Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    )

    private fun withInlayAttributes(base: InlayPresentation, attributes: TextAttributesKey): InlayPresentation {
      return WithAttributesPresentation(base, attributes, factory.editor,
                                        WithAttributesPresentation.AttributesFlags().withIsDefault(true))
    }

    private fun border(borderWidth: Int = DEFAULT_BORDER_WIDTH): InlayPresentation {
      clickListener?.run {
        val hovered = factory.delegate.onClick(
          base = factory.delegate.withReferenceAttributes(presentation),
          buttons = EnumSet.of(MouseButton.Left, MouseButton.Middle),
          onClick = { e, p ->
            onClick(e, p)
          }
        )
        presentation = ChangeOnHoverPresentation(presentation, { hovered })
      }
      val rounding = RoundWithBackgroundBorderedPresentation(
        PillWithBackgroundPresentation(
          DynamicInsetPresentation(
            presentation,
            RoundCornersInsetValueProvider(presentation, rightCornerRadius)
          ),
          backgroundAlpha = 1.0f,
        ),
        borderWidth = borderWidth
      )
      return InsetPresentation(
        rounding,
        left = onePixelBorderProvider.left,
        right = onePixelBorderProvider.right,
        top = onePixelBorderProvider.top,
        down = onePixelBorderProvider.down
      )
    }

    internal class InlayButtonPresentation(delegate: InlayPresentation) : StaticDelegatePresentation(delegate)
  }
}

private fun calculateVerticalPadding(base: InlayPresentation, textMetricsStorage: InlayTextMetricsStorage): Pair<Int, Int> {
  val padding = maxOf(getTextLineHeight(textMetricsStorage) - base.height, 0)
  val topPadding = padding / 2
  return topPadding to (padding - topPadding)
}

private fun getTextLineHeight(textMetricsStorage: InlayTextMetricsStorage): Int =
  textMetricsStorage.getFontMetrics(true).lineHeight -
  InlayButtonPresentationFactory.onePixelBorderProvider.top -
  InlayButtonPresentationFactory.onePixelBorderProvider.down

@ApiStatus.Internal
class CenteredInsetValueProvider(
  private val presentation: InlayPresentation,
  private val textMetricsStorage: InlayTextMetricsStorage,
  override val left: Int,
) : InsetValueProvider {
  override val top: Int
    get() = calculateVerticalPadding(presentation, textMetricsStorage).first
  override val down: Int
    get() = calculateVerticalPadding(presentation, textMetricsStorage).second
}

@ApiStatus.Internal
class TextInsetValueProvider(
  private val textMetricsStorage: InlayTextMetricsStorage,
  val isSmall: Boolean,
) : InsetValueProvider {
  override val top: Int
    get() = textMetricsStorage.getFontMetrics(isSmall).fontHeight - textMetricsStorage.getFontMetrics(isSmall).fontBaseline
}

@ApiStatus.Internal
class RoundCornersInsetValueProvider(
  private val presentation: InlayPresentation,
  val rightCornerRadius: Int,
) : InsetValueProvider {
  override val left: Int
    get() = presentation.height / 2
  override val right: Int
    get() = left * rightCornerRadius / InlayButtonPresentationFactory.DEFAULT_CORNER_RADIUS
}