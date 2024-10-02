// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.CSSFontResolver
import com.intellij.util.ui.ExtendableHTMLViewFactory
import com.intellij.util.ui.StyleSheetUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Color
import java.awt.Image
import java.awt.event.ActionListener
import java.net.URL
import java.util.*
import javax.swing.Icon
import javax.swing.KeyStroke
import javax.swing.text.html.StyleSheet

@Experimental
/**
 * Provides a set of configuration options for the creation of the [JBHtmlPane].
 *
 * Use [JBHtmlPaneConfiguration] constructor for default options, or
 * [JBHtmlPaneConfiguration.builder] if you want to customize anything.
 *
 * Builder supports both traditional call chaining style
 * ```java
 * JBHtmlPaneConfiguration.builder()
 *   .keyboardActions(keyboardActions)
 *   .imageResolverFactory(component -> new DocumentationImageProvider(component, imageResolver))
 *   .extensions(ExtendableHTMLViewFactory.Extensions.FIT_TO_WIDTH_IMAGES)
 *   .build()
 * ```
 * and kotlin lambda style:
 * ```kotlin
 * JBHtmlPaneConfiguration {
 *   keyboardActions(keyboardActions)
 *   imageResolverFactory = {new DocumentationImageProvider(it, imageResolver)}
 *   if (fitToWidth)
 *     extensions(ExtendableHTMLViewFactory.Extensions.FIT_TO_WIDTH_IMAGES)
 * }
 * ```
 */
class JBHtmlPaneConfiguration private constructor(builder: Builder) {
  internal val keyboardActions: Map<KeyStroke, ActionListener> = builder.keyboardActions.toMap()
  internal val imageResolverFactory: (JBHtmlPane) -> Dictionary<URL, Image>? = builder.imageResolverFactory
  internal val iconResolver: (String) -> Icon? = builder.iconResolver
  internal val customStyleSheetProviders: List<(backgroundColor: Color) -> StyleSheet> = builder.customStyleSheetProviders.toList()
  internal val fontResolver: CSSFontResolver? = builder.fontResolver
  internal val underlinedHoveredHyperlink = builder.underlinedHoveredHyperlink
  internal val extensions: List<ExtendableHTMLViewFactory.Extension> = builder.extensions.toList()

  constructor() : this(builder())

  constructor(configure: Builder.() -> Unit) : this(builder().also { configure(it) })

  class Builder {
    /**
     * Provide a set of custom actions activated on keystrokes.
     */
    val keyboardActions: MutableMap<KeyStroke, ActionListener> = mutableMapOf()

    /**
     * Provide additional resolve for images. The [JBHtmlPane] context can be used to
     * properly scale the image for HiDpi resolutions.
     */
    var imageResolverFactory: (JBHtmlPane) -> Dictionary<URL, Image>? = { null }

    /**
     * Provide additional resolve for `<icon>` elements.
     * [iconResolver] should try to provide an [Icon]
     * for a particular value of the `src` attribute of `<icon>` element.
     * If there is no [iconResolver], or [iconResolver] returns `null`,
     * icon is resolved using default mechanism. The default logic
     * is to resolve `src` to a field with icon using reflection. E.g.:
     * ```html
     * <icon src="AllIcons.Actions.CheckOut"></icon>
     * ```
     */
    var iconResolver: (String) -> Icon? = { null }

    /**
     * @see [customStyleSheetProvider]
     */
    val customStyleSheetProviders: MutableList<(backgroundColor: Color) -> StyleSheet> = mutableListOf()

    /**
     * Provide custom [fontResolver].
     * Usually this would be needed only in the context of an editor.
     * E.g.:
     * ```kotlin
     * fontResolver = EditorCssFontResolver.getInstance(editor)
     * ```
     * If you want to provide a different font resolver than
     * [com.intellij.openapi.editor.impl.EditorCssFontResolver],
     * make sure to support font family names `_EditorFont_` and `_EditorFontNoLigatures_`.
     */
    var fontResolver: CSSFontResolver? = null

    /**
     * Toggle whether hyperlinks are underlined when hovered.
     *
     * This is useful if another implementation needs to apply a different style
     * to hyperlinks when hovered (this can be done by adding an [javax.swing.event.HyperlinkListener]
     * to the [JBHtmlPane]).
     *
     * Default is true.
     */
    var underlinedHoveredHyperlink: Boolean = true

    /**
     * Provide a list of additional extensions for the [ExtendableHTMLViewFactory]
     */
    val extensions: MutableList<ExtendableHTMLViewFactory.Extension> = mutableListOf()

    fun build(): JBHtmlPaneConfiguration = JBHtmlPaneConfiguration(this)

    /**
     * Provide a set of custom actions activated on keystrokes.
     */
    fun keyboardActions(keyboardActions: Map<KeyStroke, ActionListener>): Builder =
      apply { this.keyboardActions.putAll(keyboardActions) }

    /**
     * Provide a set of custom actions activated on keystrokes.
     */
    fun keyboardActions(vararg keyboardActions: Pair<KeyStroke, ActionListener>): Builder =
      apply { this.keyboardActions.putAll(keyboardActions) }

    /**
     * Provide additional resolve for images. The [JBHtmlPane] context can be used to
     * properly scale the image for HiDpi resolutions. For SVG support, use [com.intellij.util.ui.JBImageToolkit]
     * to create images.
     */
    fun imageResolverFactory(imageResolverFactory: (JBHtmlPane) -> Dictionary<URL, Image>?): Builder =
      apply { this.imageResolverFactory = imageResolverFactory }

    /**
     * Provide additional resolve for `<icon>` elements.
     * [iconResolver] should try to provide an [Icon]
     * for a particular value of the `src` attribute of `<icon>` element.
     * If there is no [iconResolver], or [iconResolver] returns `null`,
     * icon is resolved using default mechanism. The default logic
     * is to resolve `src` to a field with icon using reflection. E.g.:
     * ```html
     * <icon src="AllIcons.Actions.CheckOut"></icon>
     * ```
     */
    fun iconResolver(iconResolver: (String) -> Icon?): Builder =
      apply { this.iconResolver = iconResolver }

    /**
     * Provide custom [StyleSheet] based on the `backgroundColor` of the [JBHtmlPane].
     * The provider will be called each time a theme is changed, or when the background
     * color of the pane changes. You should use [StyleSheetUtil.loadStyleSheet] to load
     * a [StyleSheet] from a [String]. When providing values in `px`, make sure to scale them
     * using [com.intellij.ui.scale.JBUIScale.scale]:
     * ```kotlin
     * customStyleSheetProvider {
     *    StyleSheetUtil.loadStyleSheet("div {margin: ${JBUIScale.scale(2)}px}")
     * }
     * ```
     */
    fun customStyleSheetProvider(customStyleSheetProvider: (backgroundColor: Color) -> StyleSheet): Builder =
      apply { this.customStyleSheetProviders.add(customStyleSheetProvider) }

    /**
     * Provide custom, static [StyleSheet]. Make sure to **not** scale the `px` values:
     * ```kotlin
     * customStyleSheet("div { margin: 2px }")
     * ```
     */
    fun customStyleSheet(@Language("CSS") customStyleSheet: String): Builder =
      apply {
        this.customStyleSheetProviders.add {
          StyleSheetUtil.loadStyleSheet(
            customStyleSheet.replace(PX_SIZE_REGEX) { JBUIScale.scale(it.groupValues[1].toInt()).toString() + "px" }
          )
        }
      }

    /**
     * Provide custom [fontResolver].
     * Usually this would be needed only in the context of an editor.
     * E.g.:
     * ```kotlin
     * fontResolver(EditorCssFontResolver.getInstance(editor))
     * ```
     * If you want to provide a different font resolver than
     * [com.intellij.openapi.editor.impl.EditorCssFontResolver],
     * make sure to support font family names `_EditorFont_` and `_EditorFontNoLigatures_`.
     */
    fun fontResolver(fontResolver: CSSFontResolver?): Builder =
      apply { this.fontResolver = fontResolver }

    /**
     * Provide a list of additional extensions for the [ExtendableHTMLViewFactory]
     */
    fun extensions(extensions: List<ExtendableHTMLViewFactory.Extension>): Builder =
      apply { this.extensions.addAll(extensions) }

    /**
     * Provide a list of additional extensions for the [ExtendableHTMLViewFactory]
     */
    fun extensions(vararg extensions: ExtendableHTMLViewFactory.Extension): Builder =
      apply { this.extensions.addAll(extensions) }

  }

  companion object {

    private val PX_SIZE_REGEX = Regex("""(\d+)px""")

    @JvmStatic
    fun builder(): Builder =
      Builder()

  }

}