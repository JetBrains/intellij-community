// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI
import com.intellij.ide.ui.laf.darcula.ui.DarculaSeparatorUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.*
import javax.swing.plaf.basic.BasicComboBoxEditor
import javax.swing.tree.DefaultMutableTreeNode

internal class BrowseIconsComboBox(private val customActionsSchema: CustomActionsSchema,
                                   private val parentDisposable: Disposable,
                                   withNoneItem: Boolean) : ComboBox<ActionIconInfo>() {
  private val iconsLoadedFuture: CompletableFuture<Boolean>

  init {
    iconsLoadedFuture = loadIconsAsync(withNoneItem)
    isSwingPopup = false  // in this case speed search will filter the list of items
    setEditable(true)
    setEditor(createEditor())
    setRenderer(createRenderer())
    installSelectedIconValidator()
    ComboboxSpeedSearch.installSpeedSearch(this) { info: ActionIconInfo -> info.text }
  }

  private fun loadIconsAsync(withNoneItem: Boolean): CompletableFuture<Boolean> {
    val future = CompletableFuture<Boolean>()
    ReadAction.nonBlocking(Callable { createIconsList(withNoneItem) })
      .expireWith(parentDisposable)
      .finishOnUiThread(ModalityState.any(), Consumer { icons ->
        model = DefaultComboBoxModel(icons.toTypedArray())
        future.complete(true)
      })
      .submit(AppExecutorUtil.getAppExecutorService())
    return future
  }

  private fun createIconsList(withNoneItem: Boolean): List<ActionIconInfo> {
    val defaultIcons = getDefaultIcons()
    val customIcons = getCustomIcons(customActionsSchema)
      .filter { info -> defaultIcons.find { it.iconPath == info.iconPath } == null }
    val availableIcons = getAvailableIcons()
      .filter { info -> defaultIcons.find { it.icon === info.icon } == null }
      .sortedWith(Comparator { a, b -> a.text.compareTo(b.text, ignoreCase = true) })
    val icons: MutableList<ActionIconInfo> = LinkedList()
    if (withNoneItem) icons.add(NONE)
    icons.addAll(defaultIcons)
    icons.add(SEPARATOR)
    icons.addAll(customIcons)
    icons.addAll(availableIcons)
    return icons
  }

  private fun createEditor(): ComboBoxEditor = object : BasicComboBoxEditor() {
    override fun createEditorComponent(): JTextField {
      val textField: ExtendableTextField = object : ExtendableTextField() {
        override fun requestFocus() {
          // it is required to move focus back to comboBox because otherwise speed search will not work
          this@BrowseIconsComboBox.requestFocus()
        }
      }
      textField.border = null
      textField.isEditable = false
      textField.addExtension(createBrowseIconExtension())
      textField.addExtension(object : ExtendableTextComponent.Extension {
        override fun getIcon(hovered: Boolean): Icon? {
          return (selectedItem as? ActionIconInfo)?.icon
        }

        override fun isIconBeforeText() = true
      })
      return textField
    }
  }

  private fun createRenderer(): ListCellRenderer<ActionIconInfo> = object : ColoredListCellRenderer<ActionIconInfo>() {
    override fun getListCellRendererComponent(list: JList<out ActionIconInfo>,
                                              value: ActionIconInfo?,
                                              index: Int,
                                              selected: Boolean,
                                              hasFocus: Boolean): Component {
      return if (value === SEPARATOR) {
        object : JSeparator(HORIZONTAL) {
          override fun updateUI() {
            setUI(object : DarculaSeparatorUI() {
              override fun getStripeIndent() = 0

              override fun getPreferredSize(c: JComponent) = JBUI.size(0, 1)
            })
          }
        }
      }
      else super.getListCellRendererComponent(list, value, index, selected, hasFocus)
    }

    override fun customizeCellRenderer(list: JList<out ActionIconInfo>,
                                       value: ActionIconInfo?,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean) {
      if (value != null) {
        icon = value.icon
        append(value.text)
      }
    }
  }

  private fun installSelectedIconValidator() {
    ComponentValidator(parentDisposable).withValidator(Supplier {
      val path = (selectedItem as? ActionIconInfo)?.iconPath ?: return@Supplier null
      try {
        CustomActionsSchema.loadCustomIcon(path)
        null
      }
      catch (ex: FileNotFoundException) {
        ValidationInfo(IdeBundle.message("icon.validation.message.not.found"), this)
      }
      catch (ex: NoSuchFileException) {
        ValidationInfo(IdeBundle.message("icon.validation.message.not.found"), this)
      }
      catch (ex: IOException) {
        ValidationInfo(IdeBundle.message("icon.validation.message.format"), this)
      }
    }).installOn(this)

    addActionListener(ActionListener {
      // reset validation info if some item selected
      ComponentValidator.getInstance(this).ifPresent { validator -> validator.updateInfo(null) }
    })
  }

  private fun createBrowseIconExtension(): ExtendableTextComponent.Extension {
    val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)
    val tooltip = "${UIBundle.message("component.with.browse.button.browse.button.tooltip.text")} (${KeymapUtil.getKeystrokeText(keyStroke)})"
    val browseExtension = ExtendableTextComponent.Extension.create(AllIcons.General.OpenDisk, AllIcons.General.OpenDiskHover,
                                                                   tooltip, this::browseIconAndSelect)
    object : DumbAwareAction() {
      override fun actionPerformed(e: AnActionEvent) {
        browseIconAndSelect()
      }
    }.registerCustomShortcutSet(CustomShortcutSet(keyStroke), this, parentDisposable)
    return browseExtension
  }

  private fun browseIconAndSelect() {
    val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withFileFilter { file ->
        StringUtil.equalsIgnoreCase(file.extension, "svg") || StringUtil.equalsIgnoreCase(file.extension, "png")
      }
    descriptor.title = IdeBundle.message("title.browse.icon")
    descriptor.description = IdeBundle.message("prompt.browse.icon.for.selected.action")
    val iconFile = FileChooser.chooseFile(descriptor, null, null)
    if (iconFile != null) {
      val icon = try {
        CustomActionsSchema.loadCustomIcon(iconFile.path)
      }
      catch (ex: IOException) {
        thisLogger().warn("Failed to load icon from disk, path: ${iconFile.path}", ex)
        IconManager.getInstance().getPlatformIcon(PlatformIcons.Stub)
      }
      if (icon != null) {
        val info = ActionIconInfo(icon, iconFile.name, null, iconFile.path)
        val separatorInd = model.getIndexOf(SEPARATOR)
        model.insertElementAt(info, separatorInd + 1)
        selectedIndex = separatorInd + 1
      }
    }
  }

  fun selectIconForNode(node: DefaultMutableTreeNode) {
    if (iconsLoadedFuture.isDone) {
      doSelectIconForNode(node)
    }
    else iconsLoadedFuture.thenAccept { doSelectIconForNode(node) }
  }

  private fun doSelectIconForNode(node: DefaultMutableTreeNode) {
    val pair = CustomizableActionsPanel.getActionIdAndIcon(node)
    val (actionId, icon) = pair.first to pair.second
    if (actionId != null && icon != null) {
      val customIconRef = customActionsSchema.getIconPath(actionId)
      if (StringUtil.isNotEmpty(customIconRef) && selectByCondition { info -> info.iconReference == customIconRef }
          || selectByCondition { info -> info.actionId == actionId }
          || selectByCondition { info -> info.icon == icon }) {
        return
      }
    }
    selectedIndex = 0
  }

  private fun selectByCondition(predicate: (ActionIconInfo) -> Boolean): Boolean {
    val ind = (0 until model.size).find { predicate(model.getElementAt(it)) }
    return (ind != null).also {
      if (it) selectedIndex = ind!!
    }
  }

  override fun getModel(): DefaultComboBoxModel<ActionIconInfo> {
    return super.getModel() as DefaultComboBoxModel<ActionIconInfo>
  }

  /**
   * Overriding of selecting items required to prohibit selecting of [SEPARATOR] items
   */
  override fun setSelectedIndex(anIndex: Int) {
    if (anIndex == -1) {
      selectedItem = null
    }
    else if (anIndex < 0 || anIndex >= dataModel.size) {
      throw IllegalArgumentException("setSelectedIndex: $anIndex out of bounds")
    }
    else {
      val item = dataModel.getElementAt(anIndex)
      if (item !== SEPARATOR) {
        selectedItem = item
      }
    }
  }

  /**
   * Overriding of selecting items required to prohibit selecting of [SEPARATOR] items
   */
  override fun updateUI() {
    setUI(object : DarculaComboBoxUI() {
      override fun selectNextPossibleValue() {
        val curInd = if (comboBox.isPopupVisible) listBox.selectedIndex else comboBox.selectedIndex
        selectPossibleValue(curInd, true)
      }

      override fun selectPreviousPossibleValue() {
        val curInd = if (comboBox.isPopupVisible) listBox.selectedIndex else comboBox.selectedIndex
        selectPossibleValue(curInd, false)
      }

      private fun selectPossibleValue(curInd: Int, next: Boolean) {
        if (next && curInd < comboBox.model.size - 1) {
          trySelectValue(curInd + 1, true)
        }
        else if (!next && curInd > 0) {
          trySelectValue(curInd - 1, false)
        }
      }

      private fun trySelectValue(ind: Int, next: Boolean) {
        val item = comboBox.getItemAt(ind)
        if (item !== SEPARATOR) {
          listBox.selectedIndex = ind
          listBox.ensureIndexIsVisible(ind)
          if (comboBox.isPopupVisible) {
            comboBox.selectedIndex = ind
          }
          comboBox.repaint()
        }
        else {
          selectPossibleValue(ind, next)
        }
      }
    })
  }
}