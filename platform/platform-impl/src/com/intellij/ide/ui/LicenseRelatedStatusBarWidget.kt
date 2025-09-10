// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.*
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Color
import java.awt.Graphics
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.UIManager

@ApiStatus.Internal
abstract class LicenseRelatedStatusBarWidgetFactory : StatusBarWidgetFactory {

  init {
    val messageBus = ApplicationManager.getApplication().messageBus
    messageBus.connect().subscribe(LicensingFacade.LicenseStateListener.TOPIC, LicensingFacade.LicenseStateListener {
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


@Service(Service.Level.PROJECT)
private class LicenseRelatedStatusBarWidgetProjectService(val cs: CoroutineScope)


@ApiStatus.Internal
abstract class LicenseRelatedStatusBarWidget(private val factory: LicenseRelatedStatusBarWidgetFactory) : CustomStatusBarWidget {
  final override fun ID(): String = factory.id

  private val lazyLabel: Lazy<JLabel> = lazy {
    createLabel().also {
      onComponentCreated(it)
    }
  }

  @RequiresEdt
  protected open fun onComponentCreated(label: JLabel) {
  }

  final override fun getComponent(): JComponent = lazyLabel.value

  final override fun install(statusBar: StatusBar) {
    val project = statusBar.project ?: return

    // The purpose of this listener is to guarantee that license-related widgets are the rightmost ones.
    statusBar.addListener(object : StatusBarListener {
      override fun widgetAdded(addedWidget: StatusBarWidget, anchor: @NonNls String?) {
        if (addedWidget is LicenseRelatedStatusBarWidget) return

        val myIndex = statusBar.allWidgets?.lastIndexOf(this@LicenseRelatedStatusBarWidget) ?: 0
        val rightmostLicenseUnrelated = statusBar.allWidgets?.indexOfLast { it !is LicenseRelatedStatusBarWidget } ?: 0
        if (myIndex < rightmostLicenseUnrelated) {
          statusBar.removeWidget(ID())
          val cs = project.service<LicenseRelatedStatusBarWidgetProjectService>().cs
          val widget = factory.createWidget(project, cs)
          statusBar.addWidget(widget, "last")
        }
      }
    }, this)
  }

  protected abstract val text: String
  protected open val tooltip: String? = null

  protected abstract val foreground: Color
  protected open val background: Color? = null
  protected abstract val borderColor: Color

  protected abstract fun createClickListener(): ClickListener

  @RequiresEdt
  fun updateWidget() {
    if (!lazyLabel.isInitialized()) return

    val label = lazyLabel.value
    val text = text
    val icon = label.icon as? TextIcon
    icon?.text = text
    icon?.foreground = foreground
    icon?.background = background
    icon?.borderColor = borderColor
    label.toolTipText = tooltip
    label.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, text)

    label.revalidate()
    label.repaint()
  }

  private fun createLabel(): JLabel {
    val uiSettings = UISettings.getInstance()
    val text = text
    val icon = TextIcon(text, foreground, background, borderColor, 0, true)
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


private open class WidgetLabel(image: Icon) : JLabel(image) {
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
