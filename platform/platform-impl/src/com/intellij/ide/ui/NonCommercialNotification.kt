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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.*
import com.intellij.ui.LicensingFacade.LicenseStateListener
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Graphics
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.*
import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.*
import javax.swing.event.HyperlinkEvent

private const val ID = "NonCommercial"

@ApiStatus.Internal
abstract class LicenseRelatedStatusBarWidgetFactory : StatusBarWidgetFactory {

  init {
    val messageBus = ApplicationManager.getApplication().messageBus
    messageBus.connect().subscribe(LicenseStateListener.TOPIC, LicenseStateListener {
      ApplicationManager.getApplication().invokeLater(
        {
          ProjectManager.getInstance().openProjects.forEach {
            it.service<StatusBarWidgetsManager>().updateWidget(this@LicenseRelatedStatusBarWidgetFactory)
          }
        },
        ModalityState.any())
    })
  }

  final override fun getDisplayName(): String = "" // unused
  final override fun canBeEnabledOn(statusBar: StatusBar): Boolean = false
  final override fun isConfigurable(): Boolean = false
  abstract override fun isAvailable(project: Project): Boolean
}


internal class NonCommercialFactory : LicenseRelatedStatusBarWidgetFactory() {
  override fun getId() = ID

  override fun isAvailable(project: Project): Boolean {
    val metadata = LicensingFacade.getInstance()?.metadata ?: return false
    return metadata.length > 10 && metadata[10] == 'F'
  }

  override fun createWidget(project: Project): StatusBarWidget = NonCommercialWidget(this)
}


@ApiStatus.Internal
abstract class LicenseRelatedStatusBarWidget(private val factory: LicenseRelatedStatusBarWidgetFactory) : CustomStatusBarWidget {
  final override fun ID(): String = factory.id

  private val myComponent: JLabel by lazy { createComponent() }

  final override fun getComponent(): JComponent = myComponent

  override fun install(statusBar: StatusBar) {
    val project = statusBar.project ?: return

    // The purpose of this listener is to guarantee that license-related widgets are the rightmost ones.
    statusBar.addListener(object : StatusBarListener {
      override fun widgetAdded(addedWidget: StatusBarWidget, anchor: @NonNls String?) {
        if (addedWidget is LicenseRelatedStatusBarWidget) return

        val myIndex = statusBar.allWidgets?.lastIndexOf(this@LicenseRelatedStatusBarWidget) ?: 0
        val rightmostLicenseUnrelated = statusBar.allWidgets?.indexOfLast { it !is LicenseRelatedStatusBarWidget } ?: 0
        if (myIndex < rightmostLicenseUnrelated) {
          statusBar.removeWidget(ID())
          statusBar.addWidget(factory.createWidget(project), "last")
        }
      }
    }, this)
  }

  protected abstract val text: String
  protected open val tooltip: String? = null
  protected open val foreground: JBColor = JBColor.namedColor("Badge.greenOutlineForeground", JBColor(0x208A3C, 0x5FAD65))
  protected open val borderColor: JBColor = JBColor.namedColor("Badge.greenOutlineBorderColor", JBColor(0x55A76A, 0x4E8052))

  protected abstract fun createClickListener(): ClickListener

  private fun createComponent(): JLabel {
    val uiSettings = UISettings.getInstance()
    val text = text
    val icon = TextIcon(text, foreground, null, borderColor, 0, true)
    icon.setFont(getStatusFont())
    icon.round = 18
    icon.insets = JBUI.insets(if (!ExperimentalUI.isNewUI() || uiSettings.compactMode) 3 else 4, 8)

    val label = if (ExperimentalUI.isNewUI()) {
      object : WidgetLabel(icon) {
        var compactMode = uiSettings.compactMode
        var scale = uiSettings.ideScale
        var oldFont = font

        override fun paint(g: Graphics) {
          val newValue = uiSettings.compactMode
          val newScale = uiSettings.ideScale
          if (compactMode != newValue || scale != newScale || oldFont != font) {
            compactMode = newValue
            scale = newScale
            oldFont = font
            icon.insets = JBUI.insets(if (newValue) 3 else 4, 8)
            icon.font = getStatusFont()
            icon.setFontTransform(getFontMetrics(icon.font).fontRenderContext.transform)
            parent.revalidate()
          }
          super.paint(g)
        }
      }
    }
    else {
      WidgetLabel(icon)
    }
    icon.setFontTransform(label.getFontMetrics(icon.font).fontRenderContext.transform)
    label.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, text)

    val popup = createClickListener()
    label.clickListener = popup
    popup.installOn(label)

    label.toolTipText = tooltip
    return label
  }

  private fun getStatusFont() = if (SystemInfoRt.isMac && !ExperimentalUI.isNewUI()) JBFont.small() else JBFont.medium()
}


private class NonCommercialWidget(factory: LicenseRelatedStatusBarWidgetFactory) : LicenseRelatedStatusBarWidget(factory) {
  override val text: String = IdeBundle.message("status.bar.widget.non.commercial.usage")
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
open class WidgetLabel(image: Icon) : JLabel(image) {
  var clickListener: ClickListener? = null

  override fun getAccessibleContext(): AccessibleContext? {
    if (accessibleContext == null) {
      accessibleContext = object : AccessibleJLabel(), AccessibleAction {
        override fun getAccessibleRole() = AccessibleRole.PUSH_BUTTON

        override fun getAccessibleAction() = this

        override fun getAccessibleActionCount() = 1

        override fun getAccessibleActionDescription(i: Int): String? {
          if (i == 0) {
            return UIManager.getString("AbstractButton.clickText")
          }
          return null
        }

        override fun doAccessibleAction(i: Int): Boolean {
          if (i == 0 && clickListener != null) {
            clickListener!!.onClick(MouseEvent(this@WidgetLabel, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false), 1)
            return true
          }
          return false
        }
      }
    }
    return accessibleContext
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