// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.shared

import com.intellij.ide.IdeBundle
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.Nls
import java.awt.GridBagConstraints
import java.awt.event.ActionEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.util.function.Supplier
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

internal fun gridConstraint(col: Int, row: Int): GridBagConstraints {
  return GridBagConstraints().apply {
    fill = GridBagConstraints.BOTH
    gridx = col
    gridy = row
    weightx = 1.0
    weighty = 1.0
  }
}

fun <T : JComponent> withValidation(
  builder: Cell<T>,
  errorChecks: List<TextValidationFunction>,
  warningChecks: TextValidationFunction?,
  validatedTextComponents: MutableList<JTextField>,
  parentDisposable: Disposable
): Cell<T> {
  if (errorChecks.isEmpty()) return builder

  val textField = getJTextField(builder.component)
  val validationFunc = Supplier<ValidationInfo?> {
    val text = textField.text
    for (validationUnit in errorChecks) {
      val errorMessage = validationUnit.checkText(text)
      if (errorMessage != null) {
        return@Supplier ValidationInfo(errorMessage, textField)
      }
    }

    if (warningChecks != null) {
      val warningMessage = warningChecks.checkText(text)
      if (warningMessage != null) {
        return@Supplier ValidationInfo(warningMessage, textField).asWarning().withOKEnabled()
      }
    }

    null
  }

  ComponentValidator(parentDisposable)
    .withValidator(validationFunc)
    .installOn(textField)

  textField.document.addDocumentListener(object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) {
      ComponentValidator.getInstance(textField).ifPresent { v: ComponentValidator -> v.updateInfo(null) }
    }
  })

  // use FocusListener instead of Validator.andStartOnFocusLost(), because the second one can disable validation, that can cause
  // not validating unfocused fields, that are depends on some other fields
  textField.addFocusListener(object : FocusListener {
    override fun focusGained(e: FocusEvent) {
      // ignore
    }

    override fun focusLost(e: FocusEvent) {
      revalidateAllAndHighlight(validatedTextComponents)
    }
  })
  validatedTextComponents.add(textField)

  return builder
}

fun validateFormFields(formParent: JComponent,
                       contentPanel: DialogPanel,
                       validatedComponents: List<JComponent>): Boolean {
  // look for errors
  var firstInvalidComponent: JComponent? = null
  for (component in validatedComponents) {
    ComponentValidator.getInstance(component).ifPresent { validator: ComponentValidator ->
      validator.revalidate()
      val validationInfo = validator.validationInfo
      if (validationInfo != null && !validationInfo.warning) {
        if (firstInvalidComponent == null) {
          firstInvalidComponent = component
        }
      }
    }
  }

  if (firstInvalidComponent != null) {
    contentPanel.preferredFocusedComponent = firstInvalidComponent
    return false
  }

  // look for warnings
  val warnings = mutableListOf<ValidationInfo>()
  for (component in validatedComponents) {
    ComponentValidator.getInstance(component).ifPresent { validator: ComponentValidator ->
      val validationInfo = validator.validationInfo
      if (validationInfo != null && validationInfo.warning) {
        warnings.add(validationInfo)
      }
    }
  }

  if (warnings.isNotEmpty()) {
    val message = getWarningsMessage(warnings)

    val answer = Messages.showOkCancelDialog(formParent, message,
                                             IdeBundle.message("title.warning"),
                                             Messages.getYesButton(),
                                             Messages.getCancelButton(),
                                             Messages.getWarningIcon())
    if (answer != Messages.OK) {
      return false
    }
  }

  return true
}

private fun revalidateAllAndHighlight(validatedComponents: List<JComponent>) {
  for (component in validatedComponents) {
    ComponentValidator.getInstance(component).ifPresent { validator: ComponentValidator ->
      validator.revalidate()
    }
  }
}

private fun getJTextField(component: JComponent): JTextField {
  return when (component) {
    is TextFieldWithBrowseButton -> component.textField
    is JTextField -> component
    else -> throw IllegalArgumentException()
  }
}

@NlsSafe
private fun getWarningsMessage(warnings: MutableList<ValidationInfo>): String {
  val message = StringBuilder()
  if (warnings.size > 1) {
    message.append(JavaStartersBundle.message("project.settings.warnings.group"))
    for (warning in warnings) {
      message.append("\n- ").append(warning.message)
    }
  }
  else if (warnings.isNotEmpty()) {
    message.append(warnings.first().message)
  }
  message.append("\n\n").append(JavaStartersBundle.message("project.settings.warnings.ignore"))
  return message.toString()
}

internal fun walkCheckedTree(root: CheckedTreeNode?, visitor: (CheckedTreeNode) -> Unit) {
  if (root == null) return

  fun walkTreeNode(root: TreeNode, visitor: (CheckedTreeNode) -> Unit) {
    if (root is CheckedTreeNode) {
      visitor.invoke(root)
    }

    for (child in root.children()) {
      walkTreeNode(child, visitor)
    }
  }

  walkTreeNode(root, visitor)
}

internal fun enableEnterKeyHandling(list: CheckboxTreeBase) {
  list.inputMap.put(KeyStroke.getKeyStroke("ENTER"), "pick-node")
  list.actionMap.put("pick-node", object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent?) {
      val selection = list.selectionPath
      if (selection != null) {
        if (selection.lastPathComponent is CheckedTreeNode) {
          val node = selection.lastPathComponent as CheckedTreeNode
          list.setNodeState(node, !node.isChecked)
        }
        else if (selection.lastPathComponent is DefaultMutableTreeNode) {
          if (list.isExpanded(selection)) {
            list.collapsePath(selection)
          }
          else {
            list.expandPath(selection)
          }
        }
      }
    }
  })
}

fun Row.hyperLink(@Nls title: String, @NlsSafe url: String): Cell<HyperlinkLabel> {
  val hyperlinkLabel = HyperlinkLabel(title)
  hyperlinkLabel.setHyperlinkTarget(url)
  hyperlinkLabel.toolTipText = url
  return this.cell(hyperlinkLabel)
}