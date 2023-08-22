// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.Consumer
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.border.Border

/**
 * A status bar widget.
 *
 * *Implementation note:* status bar widgets can be instantiated on a BGT,
 * so implementations should take care not to instantiate any Swing components in constructors
 * and/or field/property initializers. All initialization should happen when the components are actually needed.
 * The simplest way to achieve this is to use Kotlin's lazy initialization for the component
 * property.
 *
 * @see com.intellij.openapi.wm.StatusBarWidgetFactory
 */
interface StatusBarWidget : Disposable {
  @Suppress("FunctionName")
  fun ID(): @NonNls String

  @Suppress("DEPRECATION")
  fun getPresentation(): WidgetPresentation? {
    return getPresentation(if (SystemInfoRt.isMac) PlatformType.MAC else PlatformType.DEFAULT)
  }

  fun install(statusBar: StatusBar) {}

  override fun dispose() {}

  @Suppress("SpellCheckingInspection")
  interface Multiframe : StatusBarWidget {
    fun copy(): StatusBarWidget
  }

  interface WidgetPresentation {
    fun getTooltipText(): @NlsContexts.Tooltip String?

    fun getShortcutText(): @Nls String? = null

    fun getClickConsumer(): Consumer<MouseEvent>? = null
  }

  /**
   * Consider using [IconWidgetPresentation]
   */
  interface IconPresentation : WidgetPresentation {
    fun getIcon(): Icon?
  }

  /**
   * Consider using [TextWidgetPresentation]
   */
  interface TextPresentation : WidgetPresentation {
    fun getText(): @NlsContexts.Label String

    fun getAlignment(): Float
  }

  interface MultipleTextValuesPresentation : WidgetPresentation {
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("implement {@link #getPopup()}")
    fun getPopupStep(): ListPopup? = null

    @Suppress("DEPRECATION")
    fun getPopup(): JBPopup? = getPopupStep()

    fun getSelectedValue(): @NlsContexts.StatusBarText String?

    @Deprecated("never invoked; please drop ")
    fun getMaxValue(): String = ""

    fun getIcon(): Icon? = null
  }

  @Deprecated("do not use it ")
  enum class PlatformType {
    DEFAULT,
    MAC
  }

  @Deprecated("implement {@link #getPresentation()} instead ")
  fun getPresentation(@Suppress("unused", "DEPRECATION") type: PlatformType): WidgetPresentation? = null

  @Deprecated("Use {@link JBUI.CurrentTheme.StatusBar.Widget} border methods ")
  abstract class WidgetBorder : Border {
    companion object {
      @JvmField
      val ICON: Border = JBUI.CurrentTheme.StatusBar.Widget.iconBorder()

      @JvmField
      val INSTANCE: Border = JBUI.CurrentTheme.StatusBar.Widget.border()

      @JvmField
      val WIDE: Border = JBUI.CurrentTheme.StatusBar.Widget.border()
    }
  }
}
