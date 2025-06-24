// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.find.impl.TextSearchRightActionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.Gray
import com.intellij.ui.RowIcon
import com.intellij.ui.TextIcon
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import javax.swing.Icon

@ApiStatus.Internal
class HintHelper(private val myTextField: ExtendableTextField) {
  private val myHintTextIcon = TextIcon("", JBUI.CurrentTheme.BigPopup.searchFieldGrayForeground(), Gray.TRANSPARENT, 0)
  private val myWarnIcon = RowIcon(2, com.intellij.ui.icons.RowIcon.Alignment.BOTTOM)
  private val myHintExtension = createExtension(myHintTextIcon)
  private val mySearchProcessExtension = createExtension(AnimatedIcon.Default.INSTANCE)
  private val myWarningExtension: ExtendableTextComponent.Extension
  private val myRightExtensions: MutableList<ExtendableTextComponent.Extension> = ArrayList()

  init {
    myHintTextIcon.setFont(myTextField.getFont())
    myHintTextIcon.setFontTransform(FontInfo.getFontRenderContext(myTextField).transform)
    // Try aligning hint by baseline with the text field
    myHintTextIcon.setInsets(scale(3), 0, 0, 0)

    myWarnIcon.setIcon(AllIcons.General.Warning, 0)
    myWarnIcon.setIcon(myHintTextIcon, 1)
    myWarningExtension = createExtension(myWarnIcon)
  }

  fun setHint(hintText: String?) {
    myTextField.removeExtension(myHintExtension)
    myTextField.removeExtension(myWarningExtension)
    if (StringUtil.isNotEmpty(hintText)) {
      myHintTextIcon.setText(hintText)
      addExtensionAsLast(myHintExtension)
    }
  }

  fun setWarning(warnText: String?) {
    myTextField.removeExtension(myHintExtension)
    myTextField.removeExtension(myWarningExtension)
    if (StringUtil.isNotEmpty(warnText)) {
      myHintTextIcon.setText(warnText)
      myWarnIcon.setIcon(myHintTextIcon, 1)
      addExtensionAsLast(myWarningExtension)
    }
  }

  fun setSearchInProgress(inProgress: Boolean) {
    myTextField.removeExtension(mySearchProcessExtension)
    if (inProgress) myTextField.addExtension(mySearchProcessExtension)
  }

  //set extension which should be shown last
  fun addExtensionAsLast(ext: ExtendableTextComponent.Extension?) {
    val extensions = ArrayList<ExtendableTextComponent.Extension?>(myTextField.extensions)
    extensions.add(0, ext)
    myTextField.setExtensions(extensions)
  }

  fun setRightExtensions(actions: List<AnAction>) {
    myTextField.removeExtension(myHintExtension)
    myTextField.removeExtension(myWarningExtension)
    actions.map { createRightActionExtension(it) }.forEach { extension ->
      addExtensionAsLast(extension)
      myRightExtensions.add(extension)
    }
  }

  fun removeRightExtensions() {
    myRightExtensions.forEach(myTextField::removeExtension)
  }

  companion object {
    private fun createExtension(icon: Icon): ExtendableTextComponent.Extension {
      return ExtendableTextComponent.Extension { icon }
    }

    private fun createRightActionExtension(action: AnAction): ExtendableTextComponent.Extension {
      return object : ExtendableTextComponent.Extension {
        override fun getIcon(hovered: Boolean): Icon? {
          val presentation = action.getTemplatePresentation()
          if (action !is TextSearchRightActionAction) return presentation.getIcon()

          if (action.isSelected()) {
            return presentation.selectedIcon
          }
          else if (hovered) {
            return presentation.hoveredIcon
          }
          else {
            return presentation.getIcon()
          }
        }

        override fun getTooltip(): String? {
          return if (action is TextSearchRightActionAction)
            action.getTooltip()
          else
            action.getTemplatePresentation().description
        }

        override fun isSelected(): Boolean {
          return action is ToggleAction && action.isSelected(createActionEvent())
        }

        override fun getButtonSize(): Dimension {
          return ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
        }

        override fun getActionOnClick(): Runnable {
          return Runnable {
            action.actionPerformed(createActionEvent())
          }
        }

        fun createActionEvent(): AnActionEvent {
          return AnActionEvent.createEvent(DataContext.EMPTY_CONTEXT,
                                           action.getTemplatePresentation().clone(),
                                           ActionPlaces.POPUP,
                                           ActionUiKind.NONE,
                                           null)
        }
      }
    }
  }
}