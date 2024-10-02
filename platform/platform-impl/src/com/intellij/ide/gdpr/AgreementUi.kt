// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr

import com.intellij.DynamicBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.gdpr.ui.HtmlRtfPane
import com.intellij.idea.AppExitCodes
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.JBColor
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import java.awt.BorderLayout
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.text.html.HTMLDocument
import kotlin.system.exitProcess

fun showAgreementUi(build: AgreementUiBuilder.() -> Unit): Boolean {
  return AgreementUiBuilder().build(build)
}

class AgreementUiBuilder internal constructor() {
  private var declineButton: Pair<@NlsContexts.Button String, (DialogWrapper) -> Unit>? = null
  private var acceptButton: Pair<@NlsContexts.Button String, (DialogWrapper) -> Unit>? = null
  private var checkBoxes = mutableListOf<Pair<String, (JCheckBox) -> Unit>>()

  fun acceptButton(text: @NlsContexts.Button String, isEnabled: Boolean = true, action: (DialogWrapper) -> Unit) {
    acceptButton = text to action
    acceptButtonEnabled = isEnabled
    createdDialog?.let {
      configureAcceptButton(ui = createdDialog!!)
    }
  }

  fun declineButton(text: @NlsContexts.Button String, action: (DialogWrapper) -> Unit) {
    declineButton = text to action
    createdDialog?.let {
      configureDeclineButton(ui = createdDialog!!)
    }
  }

  fun checkBox(@NlsContexts.Checkbox text: String, action: (JCheckBox) -> Unit) {
    checkBoxes.add(text to action)
  }

  fun clearBottomPanel() {
    val bottomPanel = (createdDialog ?: return).bottomPanel!!
    bottomPanel.removeAll()
    JBUI.Borders.empty(8, 30).wrap(bottomPanel)
  }

  fun focusToText() {
    (createdDialog ?: return).viewer!!.requestFocus()
  }

  @JvmField
  var eapPanel: Boolean? = null

  private var acceptButtonEnabled = true

  @NlsSafe
  var htmlText: String = ""
    set(@NlsSafe value) {
      field = value

      val htmlRtfPane = (createdDialog ?: return).htmlRtfPane
      if (htmlRtfPane == null) {
        createdDialog!!.viewer!!.text = value
      }
      else {
        htmlRtfPane.replaceText(value).caretPosition = 0
      }
    }

  @JvmField
  var exitOnCancel: Boolean = true
  @JvmField
  var useRtfPane: Boolean = true

  @NlsContexts.DialogTitle
  var title: String? = null
    set(@NlsContexts.DialogTitle value) {
      field = value
      // yep, change dialog on the fly
      createdDialog?.title = value
    }

  fun enableAcceptButton(state: Boolean) {
    createdDialog?.acceptButton?.isEnabled = state
  }

  fun focusToAcceptButton() {
    createdDialog?.acceptButton?.requestFocus()
  }

  private var createdDialog: AgreementUi? = null

  @Suppress("HardCodedStringLiteral")
  internal fun build(build: AgreementUiBuilder.() -> Unit): Boolean {
    build(this)

    val ui = AgreementUi(htmlText = htmlText, exitOnCancel = exitOnCancel, useRtfPane = useRtfPane)
    createdDialog = ui

    configureAcceptButton(ui = ui)
    configureDeclineButton(ui)

    if (checkBoxes.isNotEmpty()) {
      ui.bottomPanel?.let { bottomPanel ->
        JBUI.Borders.empty().wrap(bottomPanel)
        for ((text, listener) in checkBoxes) {
          val checkBox = JCheckBox(text)
          bottomPanel.add(JBUI.Borders.empty(14, 30, 10, 8).wrap(checkBox), BorderLayout.CENTER)
          checkBox.addActionListener { listener(checkBox) }
        }
      }
    }

    eapPanel?.let { isPrivacyPolicy ->
      val eapPanel = JPanel(BorderLayout(0, 0))
      val bundle = DynamicBundle.getResourceBundle(this::class.java.classLoader, "messages.AgreementsBundle")
      val text =
        (if (isPrivacyPolicy) {
          bundle.getString("userAgreement.dialog.eap.consents.privacyPolicy")
        }
        else {
          bundle.getString("userAgreement.dialog.eap.consents.noPrivacyPolicy")
        }) +
        "<br/>" + bundle.getString("userAgreement.dialog.eap.consents.noPersonalData")
      val html = SwingHelper.createHtmlLabel(text, null, null)
      html.border = JBUI.Borders.empty(10, 16, 10, 0)
      html.isOpaque = true
      val eapLabelStyleSheet = (html.document as HTMLDocument).styleSheet
      eapLabelStyleSheet.addRule("a {text-decoration:none;}")
      eapPanel.add(html, BorderLayout.CENTER)
      ui.bottomPanel?.add(JBUI.Borders.empty(14, 30, 0, 30).wrap(eapPanel), BorderLayout.NORTH)
    }

    title?.let { ui.title = it }
    ui.setSize(JBUI.scale(600), JBUI.scale(460))
    ui.isModal = true
    return ui.showAndGet()
  }

  private fun configureDeclineButton(ui: AgreementUi) {
    declineButton?.let { (text, action) ->
      val declineButton = ui.declineButton ?: return@let
      declineButton.text = text
      declineButton.actionListeners.forEach { declineButton.removeActionListener(it) }
      declineButton.addActionListener(ActionListener {
        action(ui)
      })
    }
  }

  private fun configureAcceptButton(ui: AgreementUi) {
    acceptButton?.let { (text, action) ->
      val acceptButton = ui.acceptButton ?: return@let
      acceptButton.text = text
      acceptButton.actionListeners.forEach { acceptButton.removeActionListener(it) }
      acceptButton.addActionListener(ActionListener {
        action(ui)
      })

      acceptButton.isEnabled = acceptButtonEnabled
    }
  }
}

private class AgreementUi(@NlsSafe private val htmlText: String,
                          private val exitOnCancel: Boolean,
                          private val  useRtfPane: Boolean) : DialogWrapper(true) {
  @JvmField
  var bottomPanel: JPanel? = null

  @JvmField
  var htmlRtfPane: HtmlRtfPane? = null

  @JvmField
  var viewer: JEditorPane? = null

  @JvmField
  var declineButton: JButton? = null

  @JvmField
  var acceptButton: JButton? = null

  private val buttonsEastGap = JBUI.scale(15)

  init {
    init()
  }

  override fun createContentPaneBorder(): Border = JBUI.Borders.emptyBottom(12)

  override fun createButtonsPanel(buttons: List<JButton?>): JPanel {
    val buttonsPanel = layoutButtonsPanel(buttons)
    buttonsPanel.border = JBUI.Borders.emptyRight(22)
    return buttonsPanel
  }

  override fun createCenterPanel(): JComponent {
    val centerPanel = JPanel(BorderLayout(0, 0))
    val viewer = if (useRtfPane) {
      htmlRtfPane = HtmlRtfPane()
      htmlRtfPane!!.create(htmlText)
    }
    else {
      createHtmlEditorPane(htmlText = htmlText)
    }
    this.viewer = viewer

    viewer.caretPosition = 0
    viewer.isEditable = false
    viewer.border = JBUI.Borders.empty(30, 30, 30, 60)
    val scrollPane = JBScrollPane(viewer, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    val line = CustomLineBorder(UIManager.getColor("DialogWrapper.southPanelDivider") ?: JBColor.border(), 0, 0, 1, 0)
    scrollPane.border = CompoundBorder(line, JBUI.Borders.empty())
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
    declineButton = JButton(IdeBundle.message("gdpr.exit.button"))
    acceptButton = JButton(IdeBundle.message("gdpr.continue.button"))

    panel.add(buttonPanel, BorderLayout.EAST)
    buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
    buttonPanel.add(Box.createHorizontalStrut(JBUI.scale(5)))
    buttonPanel.add(declineButton)
    buttonPanel.add(Box.createHorizontalStrut(JBUI.scale(5)))
    buttonPanel.add(acceptButton)
    buttonPanel.add(Box.createRigidArea(JBUI.size(buttonsEastGap, 5)))
    declineButton!!.isEnabled = true
    acceptButton!!.isEnabled = true
    rootPane.defaultButton = acceptButton
    return panel
  }

  override fun getPreferredFocusedComponent() = viewer

  override fun doCancelAction() {
    super.doCancelAction()

    if (exitOnCancel) {
      val application = ApplicationManagerEx.getApplicationEx()
      if (application == null) {
        exitProcess(AppExitCodes.PRIVACY_POLICY_REJECTION)
      }
      else {
        application.exit(ApplicationEx.FORCE_EXIT or ApplicationEx.EXIT_CONFIRMED, AppExitCodes.PRIVACY_POLICY_REJECTION)
      }
    }
  }

  private fun createHtmlEditorPane(@NlsSafe htmlText: String): JTextPane {
    return JTextPane().apply {
      contentType = "text/html"
      addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
      editorKit = HTMLEditorKitBuilder().withGapsBetweenParagraphs().build()
      text = htmlText

      val styleSheet = (document as HTMLDocument).styleSheet
      @Suppress("SpellCheckingInspection")
      styleSheet.addRule("body {font-family: \"Segoe UI\", Tahoma, sans-serif;}")
      styleSheet.addRule("body {font-size:${JBUI.Fonts.label()}pt;}")
    }
  }
}