// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.codeInsight.lookup.impl.LookupCellRenderer.BACKGROUND_COLOR
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer.SELECTED_BACKGROUND_COLOR
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.ui.getKeyStrokes
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.util.BaseStep
import com.intellij.openapi.ui.removeKeyboardAction
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyEvent
import javax.swing.*

class TextCompletionPopup<T>(
  project: Project?,
  private val textComponent: TextCompletionField<T>,
  private val contributor: Contributor<T>,
  private val renderer: TextCompletionRenderer<T>
) : ListPopupImpl(project, null, PopupStep(contributor), null) {

  override fun getListElementRenderer(): ListCellRenderer<*> = Renderer()

  fun update() {
    listModel.updateOriginalList()

    size = Dimension(textComponent.width, list.fixedCellHeight * maxOf(1, minOf(list.model.size, list.visibleRowCount)))

    list.revalidate()
  }

  fun showUnderneathOfTextComponent() {
    show(RelativePoint(textComponent, Point(content.insets.left, textComponent.height)))
  }

  override fun process(aEvent: KeyEvent) {
    if (aEvent.keyCode != KeyEvent.VK_LEFT &&
        aEvent.keyCode != KeyEvent.VK_RIGHT) {
      super.process(aEvent)
    }
  }

  init {
    setMaxRowCount(10)
    setRequestFocus(false)

    list.prototypeCellValue = Item.None
    list.background = BACKGROUND_COLOR
    list.selectionBackground = SELECTED_BACKGROUND_COLOR
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.border = null
    list.isFocusable = false
    list.font = textComponent.font

    list.removeKeyboardAction(getKeyStrokes(IdeActions.ACTION_COPY))
    list.removeKeyboardAction(getKeyStrokes(IdeActions.ACTION_CUT))
    list.removeKeyboardAction(getKeyStrokes(IdeActions.ACTION_DELETE))
    list.removeKeyboardAction(getKeyStrokes(IdeActions.ACTION_PASTE))
    list.removeKeyboardAction(getKeyStrokes(IdeActions.ACTION_SELECT_ALL))
    list.addKeyboardAction(getKeyStrokes(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE)) {
      @Suppress("UNCHECKED_CAST")
      val selectedValue = list.selectedValue as? Item<T>
      if (selectedValue is Item.Just) {
        contributor.fireItemChosen(selectedValue.item)
      }
    }
  }

  private class PopupStep<T>(
    private val contributor: Contributor<T>,
  ) : BaseStep<Item<T>>(), ListPopupStep<Item<T>> {

    override fun isSelectable(value: Item<T>): Boolean = value is Item.Just

    override fun getValues(): List<Item<T>> {
      val items = contributor.getItems()
      if (items.isEmpty()) {
        return listOf(Item.None)
      }
      if (items.size > MAX_COMPLETION_LIST_SIZE) {
        return items.take(MAX_COMPLETION_LIST_SIZE)
                 .map { Item.Just(it) } + Item.More
      }
      return items.map { Item.Just(it) }
    }

    override fun onChosen(selectedValue: Item<T>, finalChoice: Boolean): com.intellij.openapi.ui.popup.PopupStep<*>? {
      if (selectedValue is Item.Just) {
        contributor.fireItemChosen(selectedValue.item)
      }
      return FINAL_CHOICE
    }

    override fun getTextFor(value: Item<T>): String = ""
    override fun getIconFor(value: Item<T>): Icon? = null
    override fun getSeparatorAbove(value: Item<T>): ListSeparator? = null
    override fun getDefaultOptionIndex(): Int = 0
    override fun getTitle(): String? = null
    override fun hasSubstep(selectedValue: Item<T>): Boolean = false
    override fun canceled() {}
  }

  private inner class Renderer : ColoredListCellRenderer<Item<T>>() {
    override fun customizeCellRenderer(list: JList<out Item<T>>, value: Item<T>, index: Int, selected: Boolean, hasFocus: Boolean) {
      // Code completion prefix should be visible under cell selection
      mySelected = false

      myBorder = null

      isEnabled = value is Item.Just

      when (value) {
        is Item.None -> {
          append(LangBundle.message("completion.no.suggestions"))
        }
        is Item.More -> {
          append(LangBundle.message("completion.more.suggestions", MAX_COMPLETION_LIST_SIZE))
        }
        is Item.Just -> {
          val cell = TextCompletionRenderer.Cell(this, value.item, list, index, selected, hasFocus)
          renderer.customizeCellRenderer(textComponent, cell)
        }
      }
    }
  }

  private sealed interface Item<out T> {
    object None : Item<Nothing>
    object More : Item<Nothing>
    class Just<T>(val item: T) : Item<T>
  }

  interface Contributor<T> {

    fun getItems(): List<T>

    fun fireItemChosen(item: T)
  }

  companion object {

    /**
     * This property used to truncate number of completion variants, which we show to user.
     * List popup has UI performance issues for huge number of elements.
     */
    private const val MAX_COMPLETION_LIST_SIZE = 1000
  }
}