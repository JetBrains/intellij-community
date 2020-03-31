// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr

import com.intellij.ide.gdpr.ui.HtmlRtfPane
import com.intellij.idea.Main
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.ActionListener
import java.util.*
import javax.swing.*
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.text.html.HTMLDocument
import kotlin.system.exitProcess

class AgreementUi private constructor(val htmlText: String) {

  private val bundle
    get() = ResourceBundle.getBundle("messages.AgreementsBundle")

  private var bottomPanel: JPanel? = null
  private var htmlRtfPane: HtmlRtfPane? = null
  private var viewer: JTextPane? = null

  private var declineButton: JButton? = null
  private var acceptButton: JButton? = null

  private var acceptButtonActionListener: ActionListener? = null
  private var declineButtonActionListener: ActionListener? = null

  private val buttonsEastGap = JBUI.scale(15)
  private var dialog: DialogWrapper? = null

  private fun createDialog(): AgreementUi {
    val dialogWrapper = object : DialogWrapper(true) {

      init {
        init()
      }

      override fun createContentPaneBorder(): Border = JBUI.Borders.empty(0, 0, 12, 0)

      override fun createButtonsPanel(buttons: List<JButton?>): JPanel {
        val buttonsPanel = layoutButtonsPanel(buttons)
        buttonsPanel.border = JBUI.Borders.emptyRight(22)
        return buttonsPanel
      }

      override fun createCenterPanel(): JComponent? {
        val centerPanel = JPanel(BorderLayout(0, 0))
        htmlRtfPane = HtmlRtfPane()
        viewer = htmlRtfPane?.create(htmlText)
        viewer!!.background = Color.WHITE
        viewer!!.caretPosition = 0
        viewer!!.isEditable = false
        viewer!!.border = JBUI.Borders.empty(30, 30, 30, 60)
        val scrollPane = JBScrollPane(viewer, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
        val color = UIManager.getColor("DialogWrapper.southPanelDivider")
        val line: Border = CustomLineBorder(color ?: OnePixelDivider.BACKGROUND, 0, 0, 1, 0)
        scrollPane.border = CompoundBorder(line, JBUI.Borders.empty(0))
        centerPanel.add(scrollPane, BorderLayout.CENTER)
        bottomPanel = JPanel(BorderLayout())
        JBUI.Borders.empty(16, 30, 8, 30).wrap(bottomPanel)
        centerPanel.add(bottomPanel!!, BorderLayout.SOUTH)
        scrollPane.preferredSize = JBUI.size(600, 356)
        return centerPanel
      }

      override fun createSouthPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 0))
        val buttonPanel = JPanel()
        declineButton = JButton("Exit")
        acceptButton = JButton("Continue")

        panel.add(buttonPanel, BorderLayout.EAST)
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.add(Box.createHorizontalStrut(JBUI.scale(5)))
        buttonPanel.add(declineButton)
        buttonPanel.add(Box.createHorizontalStrut(JBUI.scale(5)))
        buttonPanel.add(acceptButton)
        buttonPanel.add(Box.createRigidArea(JBUI.size(buttonsEastGap, 5)))
        declineButton!!.isEnabled = true
        acceptButton!!.isEnabled = true
        return panel
      }

      override fun getPreferredFocusedComponent(): JComponent? {
        return viewer
      }

      override fun doCancelAction() {
        super.doCancelAction()
        val application = ApplicationManager.getApplication()
        if (application == null) {
          exitProcess(Main.PRIVACY_POLICY_REJECTION)
        }
        else {
          application.exit(true, true, false)
        }
      }
    }
    dialog = dialogWrapper
    return this
  }

  fun setTitle(title: String): AgreementUi {
    dialog?.title = title
    return this
  }

  fun addCheckBox(checkBoxText: String, checkBoxListener: (JCheckBox) -> Unit): AgreementUi {
    val checkBox = JCheckBox(checkBoxText)
    bottomPanel?.add(JBUI.Borders.empty(14, 30, 10, 8).wrap(checkBox), BorderLayout.CENTER)
    JBUI.Borders.empty().wrap(bottomPanel)
    checkBox.addActionListener { checkBoxListener(checkBox) }
    return this
  }

  fun addEapPanel(isPrivacyPolicy: Boolean): AgreementUi {
    val eapPanel = JPanel(BorderLayout(0, 0))
    val text =
      (if (isPrivacyPolicy)
        bundle.getString("userAgreement.dialog.eap.consents.privacyPolicy")
      else
        bundle.getString("userAgreement.dialog.eap.consents.noPrivacyPolicy")) +
      "<br/>" + bundle.getString("userAgreement.dialog.eap.consents.noPersonalData")
    val html: JEditorPane = SwingHelper.createHtmlLabel(text, null, null)
    html.border = JBUI.Borders.empty(10, 16, 10, 0)
    html.isOpaque = true
    html.background = Color(0xDCE4E8)
    val eapLabelStyleSheet = (html.document as HTMLDocument).styleSheet
    eapLabelStyleSheet.addRule("a {color:#4a78c2;}")
    eapLabelStyleSheet.addRule("a {text-decoration:none;}")
    eapPanel.add(html, BorderLayout.CENTER)
    bottomPanel?.add(JBUI.Borders.empty(14, 30, 0, 30).wrap(eapPanel), BorderLayout.NORTH)
    return this
  }

  fun clearBottomPanel(): AgreementUi {
    bottomPanel?.removeAll()
    JBUI.Borders.empty(8, 30, 8, 30).wrap(bottomPanel)
    return this
  }

  fun setText(newHtmlText: String): AgreementUi {
    val pane = htmlRtfPane?.replaceText(newHtmlText)
    pane?.caretPosition = 0
    return this
  }

  fun focusToText(): AgreementUi {
    viewer?.requestFocus()
    return this
  }

  fun focusToAcceptButton(): AgreementUi {
    acceptButton?.requestFocus()
    return this
  }

  fun focusToDeclineButton(): AgreementUi {
    declineButton?.requestFocus()
    return this
  }

  fun setAcceptButton(text: String, isEnabled: Boolean = true, action: (DialogWrapper) -> Unit): AgreementUi {
    acceptButton?.text = text
    if (acceptButtonActionListener != null)
      acceptButton?.removeActionListener(acceptButtonActionListener)
    acceptButtonActionListener = ActionListener {
      action(dialog!!)
    }
    acceptButton?.addActionListener(acceptButtonActionListener)
    if (!isEnabled) acceptButton?.isEnabled = false
    return this
  }

  fun enableAcceptButton(state: Boolean): AgreementUi {
    acceptButton?.isEnabled = state
    return this
  }

  fun enableDeclineButton(state: Boolean): AgreementUi {
    declineButton?.isEnabled = state
    return this
  }

  fun setDeclineButton(text: String, action: (DialogWrapper) -> Unit): AgreementUi {
    declineButton?.text = text
    if (declineButtonActionListener != null)
      declineButton?.removeActionListener(declineButtonActionListener)
    declineButtonActionListener = ActionListener {
      action(dialog!!)
    }
    declineButton?.addActionListener(declineButtonActionListener)
    return this
  }

  fun pack(): DialogWrapper {
    if (dialog == null) throw IllegalStateException("Dialog hasn't been created.")
    dialog!!.pack()
    dialog!!.setSize(JBUI.scale(600), JBUI.scale(460))
    dialog!!.isModal = true
    return dialog!!
  }

  companion object {
    fun create(htmlText: String = ""): AgreementUi {
      return AgreementUi(htmlText).createDialog()
    }
  }

}