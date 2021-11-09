// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.collaboration.ui.codereview.InlineIconButton
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorTextField
import com.intellij.ui.ListFocusTraversalPolicy
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import icons.CollaborationToolsIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.*
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class SubmittableTextField(
  @NlsActions.ActionText actionName: String,
  private val model: SubmittableTextFieldModel,
  authorLabel: LinkLabel<out Any>? = null,
  onCancel: (() -> Unit)? = null
) : JPanel(null) {
  companion object {
    private val SUBMIT_SHORTCUT_SET = CommonShortcuts.CTRL_ENTER
    private val CANCEL_SHORTCUT_SET = CommonShortcuts.ESCAPE

    fun getEditorTextFieldVerticalOffset() = if (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) 6 else 4
  }

  init {
    val textField = createTextField(actionName)
    val submitButton = createSubmitButton(actionName)
    val cancelButton = createCancelButton()

    val busyLabel = JLabel(AnimatedIcon.Default())
    val textFieldWithOverlay = createTextFieldWithOverlay(textField, submitButton, busyLabel)

    isOpaque = false
    layout = MigLayout(LC().gridGap("0", "0")
                         .insets("0", "0", "0", "0")
                         .fillX())

    if (authorLabel != null) {
      isFocusCycleRoot = true
      isFocusTraversalPolicyProvider = true
      focusTraversalPolicy = ListFocusTraversalPolicy(listOf(textField, authorLabel))
      add(authorLabel, CC().alignY("top").gapRight("${JBUIScale.scale(6)}"))
    }

    add(textFieldWithOverlay, CC().grow().pushX())
    add(cancelButton, CC().alignY("top").hideMode(3))

    Controller(this, textField, busyLabel, submitButton, cancelButton, onCancel)
    UiNotifyConnector(textField, ValidatorActivatable(textField), false)

    installScrollIfChangedController()
  }

  private fun installScrollIfChangedController() {
    fun scroll() {
      scrollRectToVisible(Rectangle(0, 0, width, height))
    }

    model.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        scroll()
      }
    })

    // previous listener doesn't work properly when text field's size is changed because
    // component is not resized at this moment, so we need to handle resizing too
    // it also produces such behavior: resize of the ancestor will scroll to the field
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        if (UIUtil.isFocusAncestor(this@SubmittableTextField)) {
          scroll()
        }
      }
    })
  }

  private inner class ValidatorActivatable(private val textField: EditorTextField) : Activatable {

    private var validatorDisposable: Disposable? = null
    private var validator: ComponentValidator? = null

    init {
      model.addStateListener {
        validator?.revalidate()
      }
    }

    override fun showNotify() {
      validatorDisposable = Disposer.newDisposable("ETF validator")
      validator = ComponentValidator(validatorDisposable!!).withValidator(Supplier {
        model.error?.let { ValidationInfo(it.message.orEmpty(), textField) }
      }).installOn(textField)
    }

    override fun hideNotify() {
      validatorDisposable?.let { Disposer.dispose(it) }
      validatorDisposable = null
      validator = null
    }
  }

  private fun createTextField(@Nls placeHolder: String): EditorTextField {

    return object : EditorTextField(model.document, model.project, FileTypes.PLAIN_TEXT) {
      //always paint pretty border
      override fun updateBorder(editor: EditorEx) = setupBorder(editor)

      override fun createEditor(): EditorEx {
        // otherwise border background is painted from multiple places
        return super.createEditor().apply {
          //TODO: fix in editor
          //com.intellij.openapi.editor.impl.EditorImpl.getComponent() == non-opaque JPanel
          // which uses default panel color
          component.isOpaque = false
          //com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder.paintBorder
          scrollPane.isOpaque = false
        }
      }

      override fun getData(dataId: String): Any? {
        if (PlatformCoreDataKeys.FILE_EDITOR.`is`(dataId)) {
          return editor?.let { TextEditorProvider.getInstance().getTextEditor(it) } ?: super.getData(dataId)
        }
        return super.getData(dataId)
      }
    }.apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      setOneLineMode(false)
      setPlaceholder(placeHolder)
      addSettingsProvider {
        it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
        it.colorsScheme.lineSpacing = 1f
        it.settings.isUseSoftWraps = true
      }
      selectAll()
    }
  }

  @Suppress("DialogTitleCapitalization")
  private fun createSubmitButton(@NlsActions.ActionText actionName: String) =
    InlineIconButton(
      CollaborationToolsIcons.Send, CollaborationToolsIcons.SendHovered,
      tooltip = actionName,
      shortcut = SUBMIT_SHORTCUT_SET
    ).apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

  private fun createCancelButton() =
    InlineIconButton(AllIcons.Actions.Close, AllIcons.Actions.CloseHovered,
                     tooltip = Messages.getCancelButton(),
                     shortcut = CANCEL_SHORTCUT_SET).apply {
      border = JBUI.Borders.empty(getEditorTextFieldVerticalOffset(), 0)
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

  private fun createTextFieldWithOverlay(textField: EditorTextField, button: JComponent, busyLabel: JComponent): JComponent {

    val bordersListener = object : ComponentAdapter(), HierarchyListener {
      override fun componentResized(e: ComponentEvent?) {
        val scrollPane = (textField.editor as? EditorEx)?.scrollPane ?: return
        val buttonSize = button.size
        JBInsets.removeFrom(buttonSize, button.insets)
        scrollPane.viewportBorder = JBUI.Borders.emptyRight(buttonSize.width)
        scrollPane.viewport.revalidate()
      }

      override fun hierarchyChanged(e: HierarchyEvent?) {
        val scrollPane = (textField.editor as? EditorEx)?.scrollPane ?: return
        button.border = EmptyBorder(scrollPane.border.getBorderInsets(scrollPane))
        componentResized(null)
      }
    }

    textField.addHierarchyListener(bordersListener)
    button.addComponentListener(bordersListener)

    val layeredPane = object : JLayeredPane() {
      override fun getPreferredSize(): Dimension {
        return textField.preferredSize
      }

      override fun doLayout() {
        super.doLayout()
        textField.setBounds(0, 0, width, height)
        val preferredButtonSize = button.preferredSize
        button.setBounds(width - preferredButtonSize.width, height - preferredButtonSize.height,
                         preferredButtonSize.width, preferredButtonSize.height)
        busyLabel.bounds = SingleComponentCenteringLayout.getBoundsForCentered(textField, busyLabel)
      }
    }
    layeredPane.add(textField, JLayeredPane.DEFAULT_LAYER, 0)
    layeredPane.add(busyLabel, JLayeredPane.POPUP_LAYER, 1)
    layeredPane.add(button, JLayeredPane.POPUP_LAYER, 2)

    return layeredPane
  }

  private inner class Controller(private val panel: JPanel,
                                 private val textField: EditorTextField,
                                 private val busyLabel: JLabel,
                                 private val submitButton: InlineIconButton,
                                 cancelButton: InlineIconButton,
                                 onCancel: (() -> Unit)?) {
    init {
      textField.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          update()
          panel.revalidate()
        }
      })

      submitButton.actionListener = ActionListener { submit() }

      object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) = submit()
      }.registerCustomShortcutSet(SUBMIT_SHORTCUT_SET, textField)

      cancelButton.isVisible = onCancel != null
      if (onCancel != null) {
        cancelButton.actionListener = ActionListener { onCancel() }

        object : DumbAwareAction() {
          override fun actionPerformed(e: AnActionEvent) {
            onCancel()
          }
        }.registerCustomShortcutSet(CANCEL_SHORTCUT_SET, textField)
      }

      model.addStateListener(::update)
      update()
    }

    private fun isSubmitAllowed(): Boolean = !model.isBusy && textField.text.isNotBlank()

    private fun submit() {
      if (isSubmitAllowed()) {
        model.submit()
      }
    }

    private fun update() {
      busyLabel.isVisible = model.isBusy
      submitButton.isEnabled = isSubmitAllowed()
    }
  }
}