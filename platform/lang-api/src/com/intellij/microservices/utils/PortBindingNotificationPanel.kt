package com.intellij.microservices.utils

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.microservices.MicroservicesBundle
import com.intellij.microservices.http.request.NavigatorHttpRequest
import com.intellij.microservices.http.request.RequestNavigator
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.DropDownLink
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class PortBindingNotificationPanel(private val project: Project,
                                   request: NavigatorHttpRequest?,
                                   private val hint: String,
                                   applicationPort: Int,
                                   private val resolvedPort: Int) : JPanel(BorderLayout()) {
  private val messagePanel: JPanel
  private var link: JComponent

  init {
    background = EditorColorsManager.getInstance().globalScheme.getColor(HintUtil.PROMOTION_PANE_KEY)
    border = JBUI.Borders.empty(5, 3)

    val messageLabel = JLabel(MicroservicesBundle.message("microservices.port.binding.notification.text", applicationPort))
    link = createLink(request)

    messagePanel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 0))
    messagePanel.isOpaque = false
    messagePanel.add(messageLabel)
    messagePanel.add(link)
    add(messagePanel, BorderLayout.WEST)
  }

  fun registerProcessListener(processHandler: ProcessHandler, parentDisposable: Disposable?) {
    val processListener = object : ProcessAdapter() {
      override fun processTerminated(event: ProcessEvent) {
        if (link is ActionLink) {
          link.isEnabled = false
        }
      }
    }
    if (parentDisposable != null) {
      processHandler.addProcessListener(processListener, parentDisposable)
    }
    else {
      processHandler.addProcessListener(processListener)
    }
  }

  fun setRequest(request: NavigatorHttpRequest?) {
    val newLink = createLink(request)
    val oldLink = link
    link = newLink

    messagePanel.remove(oldLink)
    messagePanel.add(newLink)
    messagePanel.revalidate()
    messagePanel.repaint()
  }

  private fun createLink(request: NavigatorHttpRequest?): JComponent {
    val navigators = if (request != null) RequestNavigator.getRequestNavigators(request) else emptyList()
    return when {
      navigators.isEmpty() -> {
        JLabel("$resolvedPort")
      }
      navigators.size == 1 -> {
        ActionLink("$resolvedPort") { navigators[0].navigate(project, request!!, hint) }
          .apply { autoHideOnDisable = false }
      }
      else -> {
        DropDownLink(resolvedPort) {
          JBPopupFactory.getInstance().createListPopup(RequestNavigatorPopupStep(project, request!!, hint, navigators))
        }.apply { autoHideOnDisable = false }
      }
    }
  }

  override fun updateUI() {
    super.updateUI()
    border = JBUI.Borders.empty(5, 3)
  }

  private class RequestNavigatorPopupStep(private val project: Project,
                                          private val request: NavigatorHttpRequest,
                                          private val hint: String,
                                          navigators: List<RequestNavigator>) :
    BaseListPopupStep<RequestNavigator>(null, navigators) {

    override fun getIconFor(navigator: RequestNavigator): Icon? = navigator.icon

    override fun getTextFor(navigator: RequestNavigator): String = navigator.displayText

    override fun onChosen(navigator: RequestNavigator, finalChoice: Boolean): PopupStep<*>? {
      return doFinalStep {
        navigator.navigate(project, request, hint)
      }
    }
  }
}