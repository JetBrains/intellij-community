// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.find.impl.TextSearchRightActionAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.Gray
import com.intellij.ui.LayeredIcon
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
  private val myText = TextIcon("", JBUI.CurrentTheme.BigPopup.searchFieldGrayForeground(), Gray.TRANSPARENT, 0)
  private val myLoadingIcon = LayeredIcon(2)
  private val myExtensionWithHintText = ExtendableTextComponent.Extension { myText }
  private val mySearchProcessExtension = ExtendableTextComponent.Extension { AnimatedIcon.Default.INSTANCE }
  private var myExtensionWithLoadingText: ExtendableTextComponent.Extension? = null
  private val myRightExtensions: MutableList<ExtendableTextComponent.Extension> = ArrayList()

  private var myIsSearchInProgress = false

  init {
    myText.setFont(myTextField.getFont())
    myText.setFontTransform(FontInfo.getFontRenderContext(myTextField).transform)
    // Try aligning hint by baseline with the text field
    myText.setInsets(scale(3), scale(3), 0, 0)

    myLoadingIcon.setIcon(AnimatedIcon.Default.INSTANCE, 0)
  }

  fun setHint(hintText: String?) {
    myTextField.removeExtension(myExtensionWithHintText)
    myExtensionWithLoadingText?.let { myTextField.removeExtension(it) }
    myExtensionWithLoadingText = null
    if (StringUtil.isNotEmpty(hintText)) {
      myText.setText(hintText)
      addExtensionAsLast(myExtensionWithHintText)
    }
    if (myIsSearchInProgress) myTextField.addExtension(mySearchProcessExtension)
  }

  fun setLoadingText(text: String?, tooltip: @NlsContexts.Tooltip String? = null) {
    myTextField.removeExtension(myExtensionWithHintText)
    myExtensionWithLoadingText?.let { myTextField.removeExtension(it) }
    myExtensionWithLoadingText = null
    if (StringUtil.isNotEmpty(text)) {
      myText.setText(text)
      val defaultAnimatedIcon = AnimatedIcon.Default.INSTANCE
      myLoadingIcon.setIcon(myText, 1, defaultAnimatedIcon.iconWidth, (defaultAnimatedIcon.iconHeight - myText.iconHeight) / 2)
      myExtensionWithLoadingText = object : ExtendableTextComponent.Extension {
        override fun getIcon(hovered: Boolean): Icon = myLoadingIcon
        override fun getTooltip(): @NlsContexts.Tooltip String? = tooltip
      }
      addExtensionAsLast(myExtensionWithLoadingText)
      myTextField.removeExtension(mySearchProcessExtension) // don't duplicate loading icons
    }
    else if (myIsSearchInProgress) myTextField.addExtension(mySearchProcessExtension)
  }

  fun setSearchInProgress(inProgress: Boolean) {
    myIsSearchInProgress = inProgress

    myTextField.removeExtension(mySearchProcessExtension)
    if (inProgress && myExtensionWithLoadingText == null) {
      myTextField.addExtension(mySearchProcessExtension)
    }
  }

  //set extension which should be shown last
  fun addExtensionAsLast(ext: ExtendableTextComponent.Extension?) {
    val extensions = ArrayList<ExtendableTextComponent.Extension?>(myTextField.extensions)
    extensions.add(0, ext)
    myTextField.setExtensions(extensions)
  }

  fun setRightExtensions(actions: List<AnAction>) {
    myTextField.removeExtension(myExtensionWithHintText)
    myExtensionWithLoadingText?.let { myTextField.removeExtension(it) }
    actions.map { createRightActionExtension(it) }.forEach { extension ->
      addExtensionAsLast(extension)
      myRightExtensions.add(extension)
    }
  }

  fun removeRightExtensions() {
    myRightExtensions.forEach(myTextField::removeExtension)
  }

  companion object {
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