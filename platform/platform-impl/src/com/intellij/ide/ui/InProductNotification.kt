// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.SettingsEntryPointAction.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.*
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList

/**
 * @author Alexander Lobas
 */
@Internal
class InProductNotificationActionProvider : ActionProvider, IconCustomizer {
  override fun getUpdateActions(context: DataContext): Collection<UpdateAction?> {
    return emptyList()
  }

  private fun getExpiresInDays(): Int {
    if (!InProductNotificationDialog.enabled()) {
      return -1
    }

    val metadata = LicensingFacade.getInstance()?.metadata ?: return -1

    if (metadata.length > 10 && metadata[10] == 'E') {
      val date = LicensingFacade.getInstance()?.expirationDate ?: return -1
      val days = ChronoUnit.DAYS.between(Instant.now(), date.toInstant()).toInt()

      return days
    }

    return -1
  }

  override fun getLastActions(context: DataContext): Collection<LastAction?> {
    val days = getExpiresInDays()
    if (0 <= days && days < 15) {
      return listOf(InProductNotificationAction(days))
    }
    return emptyList()
  }

  override fun getCustomIcon(supplier: BadgeIconSupplier): Icon? {
    val days = getExpiresInDays()
    if (0 <= days && days < 15) {
      return supplier.infoIcon
    }
    return null
  }

  override fun getTooltip(): @Nls String? {
    val days = getExpiresInDays()
    if (0 <= days && days < 15) {
      return IdeBundle.message("in.product.notification.action.tooltip", ApplicationInfo.getInstance().fullApplicationName, days)
    }
    return null
  }
}

private class InProductNotificationAction(val days: Int) : LastAction(), CustomComponentAction {
  private var myFirstActionUnderline = false
  private var mySecondActionUnderline = false

  private var myFirstAction: JComponent? = null
  private var mySecondAction: JComponent? = null
  private var myComponent: JComponent? = null

  init {
    templatePresentation.text = IdeBundle.message("in.product.notification.action.default.text")
    templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Always
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val banner = InlineBanner(IdeBundle.message("in.product.notification.action.text", days),
                              EditorNotificationPanel.Status.Info)
    banner.showCloseButton(false)

    banner.addAction(IdeBundle.message("in.product.notification.action.renew.button"), {})

    banner.addAction(IdeBundle.message("in.product.notification.action.discount.button"), {})

    setPaintUnderlineForActions(banner)

    val component = OpaquePanel(BorderLayout(), JBUI.CurrentTheme.Popup.BACKGROUND)
    component.border = JBUI.Borders.empty(12)
    component.add(banner)
    myComponent = component

    return component
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (NewUI.isEnabled()) {
      val event = e.inputEvent
      if (event is MouseEvent) {
        if (isAction(event, myFirstAction)) {
          closePopup(event)
          renewLicense()
        }
        else if (isAction(event, mySecondAction)) {
          closePopup(event)
          getDiscount()
        }
      }
    }
    else {
      val dialog = InProductNotificationDialog(e.project, days, 0)
      dialog.show()
      dialog.handleAction()
    }
  }

  override fun handleMouseMove(event: MouseEvent) {
    handleMouseEvent(isAction(event, myFirstAction), isAction(event, mySecondAction), event.component)
  }

  override fun handleMouseExit(event: MouseEvent) {
    handleMouseEvent(false, false, event.component)
  }

  private fun setPaintUnderlineForActions(banner: InlineBanner) {
    val linkLabels = UIUtil.findComponentsOfType(banner, LinkLabel::class.java)
    if (linkLabels.size >= 2) {
      myFirstAction = linkLabels[0]
      mySecondAction = linkLabels[1]

      setPaintUnderline(linkLabels[0], myFirstActionUnderline)
      setPaintUnderline(linkLabels[1], mySecondActionUnderline)
    }
    else {
      myFirstActionUnderline = false
      mySecondActionUnderline = false

      myFirstAction = null
      myFirstAction = null
    }
  }

  private fun setPaintUnderline(label: LinkLabel<*>, value: Boolean) {
    if (value) {
      label.entered(MouseEvent(label, 0, 0, 0, 0, 0, 0, false))
      label.setPaintUnderline(true)
    }
  }

  private fun handleMouseEvent(firstActionUnderline: Boolean, secondActionUnderline: Boolean, component: Component) {
    val oldFirstActionUnderline = myFirstActionUnderline
    myFirstActionUnderline = firstActionUnderline

    val oldSecondActionUnderline = mySecondActionUnderline
    mySecondActionUnderline = secondActionUnderline

    if (oldFirstActionUnderline != firstActionUnderline || oldSecondActionUnderline != secondActionUnderline) {
      component.repaint()
    }
  }

  private fun isAction(event: MouseEvent, action: JComponent?): Boolean {
    val list = event.component as JList<*>
    val index = list.locationToIndex(event.getPoint())
    val location = list.indexToLocation(index)

    if (list.selectedIndex != index) {
      list.selectedIndex = index
    }

    myComponent?.bounds = list.getCellBounds(index, index)
    UIUtil.uiTraverser(myComponent).forEach { it.doLayout() }

    return myComponent?.findComponentAt(event.x - location.x, event.y - location.y) === action
  }

  private fun closePopup(event: MouseEvent) {
    ClientProperty.get(event.component, POPUP)?.closeOk(event)
  }
}

private fun renewLicense() {
  BrowserUtil.browse("https://www.jetbrains.com/shop/eform/students/prolong")
}

private fun getDiscount() {
  BrowserUtil.browse("https://www.jetbrains.com/community/education/#students/renewal")
}

@Internal
class InProductNotificationDialog(project: Project?, val days: Int, val time: Long) :
  LicenseExpirationDialog(project, "/images/Expiration.png", 377, 242) {

  companion object {
    @JvmStatic
    fun enabled(): Boolean = Registry.`is`("edu.pack.in.product.notification.enabled", true)

    @JvmStatic
    private var showTime: Long = 0
  }

  init {
    initDialog(IdeBundle.message("in.product.notification.action.default.text"))
  }

  override fun createPanel(): JComponent {
    val configuration = JBHtmlPaneConfiguration.builder().customStyleSheet("h1 { margin: 0px; padding: 0px; }").build()

    val message = JBHtmlPane(JBHtmlPaneStyleConfiguration(), configuration)
    message.text = IdeBundle.message("in.product.notification.dialog.text", days)
    message.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)

    return message
  }

  override fun getOKActionText(): @Nls String = IdeBundle.message("in.product.notification.action.renew.button")

  override fun getCancelActionText(): @Nls String = IdeBundle.message("in.product.notification.action.dismiss.button")

  override fun show() {
    if (time > 0) {
      if (showTime > 0 && (time - showTime) < 360000) {
        return
      }
      showTime = time
    }

    super.show()

    if (time > 0) {
      showTime = System.currentTimeMillis()
    }
  }

  fun handleAction() {
    if (exitCode == OK_EXIT_CODE) {
      renewLicense()
    }
  }
}