// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.BundleBase
import com.intellij.ide.IdeBundle
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.ClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.LicensingFacade
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StyleSheetUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.*
import javax.accessibility.AccessibleContext
import javax.swing.JEditorPane
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.event.HyperlinkEvent

private const val ID = "NonCommercial"

internal class NonCommercialFactory : LicenseRelatedStatusBarWidgetFactory() {
  override fun getId() = ID

  override fun isAvailable(project: Project): Boolean {
    val metadata = LicensingFacade.getInstance()?.metadata ?: return false
    return metadata.length > 10 && metadata[10] == 'F'
  }

  override fun createWidget(project: Project): StatusBarWidget = NonCommercialWidget(this)
}


private class NonCommercialWidget(factory: LicenseRelatedStatusBarWidgetFactory) : LicenseRelatedStatusBarWidget(factory) {
  override val text: String = IdeBundle.message("status.bar.widget.non.commercial.usage")

  override val foreground: Color = JBColor.namedColor("Badge.greenOutlineForeground", JBColor(0x208A3C, 0x5FAD65))
  override val borderColor: Color = JBColor.namedColor("Badge.greenOutlineBorderColor", JBColor(0x55A76A, 0x4E8052))

  override fun createClickListener(): ClickListener = NonCommercialPopup(this)
}

private class NonCommercialPopup(private val widget: NonCommercialWidget) : ClickListener() {

  override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
    NonCommercialWidgetUsagesCollector.widgetClick.log()

    val popupDisposable = Disposer.newDisposable(widget)
    val panel = createPanel(popupDisposable)
    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel.preferredFocusedComponent)
      .setRequestFocus(true).createPopup()

    val dimension = popup.content.preferredSize
    val at = Point(event.component.width - dimension.width, -dimension.height)

    Disposer.register(popupDisposable, popup)
    popup.show(RelativePoint(event.component, at))

    return true
  }

  private fun createPanel(popupDisposable: Disposable): DialogPanel {
    return panel {
      row {
        val styleSheet = StyleSheetUtil.getDefaultStyleSheet()
        styleSheet.addStyleSheet(StyleSheetUtil.loadStyleSheet("ul { margin-top: 0; }"))
        styleSheet.addStyleSheet(StyleSheetUtil.loadStyleSheet("li { margin-top: 5px }"))
        val kit = HTMLEditorKitBuilder().withStyleSheet(styleSheet).build()

        val url = getPurchaseUrl()

        val component = text("").component
        component.editorKit = kit
        component.isFocusable = true
        component.text = IdeBundle.message("popup.text.non.commercial.usage", BundleBase.replaceMnemonicAmpersand(url))
        component.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, StringUtil.stripHtml(component.text, " "))

        component.addHyperlinkListener { event ->
          val eventUrl = event.url?.toExternalForm()
          if (event.eventType == HyperlinkEvent.EventType.ACTIVATED && eventUrl != null) {
            if (eventUrl.contains("/buy/")) {
              NonCommercialWidgetUsagesCollector.widgetBuyLinkClick.log()
            }
            else {
              NonCommercialWidgetUsagesCollector.widgetAgreementLinkClick.log()
            }
          }
        }

        bottomGap(BottomGap.SMALL)
      }
      row {
        button(IdeBundle.message("popup.license.button.non.commercial.usage")) {
          showLicenseDialog(popupDisposable)
        }
      }
    }.also {
      it.border = JBUI.Borders.empty(16, 20, 12, 24)

      it.isFocusCycleRoot = true
      it.focusTraversalPolicy = LayoutFocusTraversalPolicy()
      it.preferredFocusedComponent = UIUtil.findComponentOfType(it, JEditorPane::class.java)
    }
  }

  private fun showLicenseDialog(popupDisposable: Disposable) {
    val action = ActionManager.getInstance().getAction("Register")
    val dataContext = DataContext {
      return@DataContext null
    }

    Disposer.dispose(popupDisposable)
    action?.actionPerformed(AnActionEvent.createFromDataContext("", Presentation(), dataContext))
  }

  private fun getPurchaseUrl(): @NlsSafe String {
    val tag = Locale.getDefault().toLanguageTag().lowercase()

    val product = when (LicensingFacade.getInstance()?.platformProductCode) {
      "AC" -> "AppCode"
      "QA" -> "Aqua"
      "CL" -> "CLion"
      "DB" -> "DataGrip"
      "DS" -> "DataSpell"
      "GO" -> "GoLand"
      "II" -> "Idea"
      "PC" -> "PyCharm"
      "PS" -> "PhpStorm"
      "RD" -> "Rider"
      "RM" -> "RubyMine"
      "RR" -> "RustRover"
      "WS" -> "WebStorm"

      else -> {
        if (PlatformUtils.isJetBrainsClient() || PlatformUtils.isGateway()) {
          "Idea"
        }
        else {
          PlatformUtils.getPlatformPrefix()
        }
      }
    }.lowercase()

    return "https://www.jetbrains.com/$product/buy/?fromIDE&lang=$tag"
  }
}

@ApiStatus.Internal
internal object NonCommercialWidgetUsagesCollector : CounterUsagesCollector() {
  private val nonCommercialUseGroup = EventLogGroup("non.commercial.use", 1)

  internal val widgetClick = nonCommercialUseGroup.registerEvent("widget.click")
  internal val widgetBuyLinkClick = nonCommercialUseGroup.registerEvent("widget.buy.link.click")
  internal val widgetAgreementLinkClick = nonCommercialUseGroup.registerEvent("widget.agreement.link.click")

  override fun getGroup(): EventLogGroup {
    return nonCommercialUseGroup
  }
}