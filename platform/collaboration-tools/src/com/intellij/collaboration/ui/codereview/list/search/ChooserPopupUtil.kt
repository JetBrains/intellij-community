// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.execution.ui.FragmentedSettingsUtil
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.ui.popup.util.RoundedCellRenderer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.Icon
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

object ChooserPopupUtil {

  suspend fun <T> showChooserPopup(point: RelativePoint,
                                   items: List<T>,
                                   presenter: (T) -> PopupItemPresentation,
                                   popupConfig: PopupConfig = PopupConfig.DEFAULT): T? =
    showChooserPopup(point, items, { presenter(it).shortText }, createSimpleItemRenderer(presenter), popupConfig)

  suspend fun <T> showChooserPopup(point: RelativePoint,
                                   items: List<T>,
                                   filteringMapper: (T) -> String,
                                   renderer: ListCellRenderer<T>,
                                   popupConfig: PopupConfig = PopupConfig.DEFAULT): T? {
    val listModel = CollectionListModel(items)
    val list = createList(listModel, renderer)

    @Suppress("UNCHECKED_CAST")
    val popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setFilteringEnabled { filteringMapper(it as T) }
      .setResizable(true)
      .setMovable(true)
      .setFilterAlwaysVisible(popupConfig.alwaysShowSearchField)
      .createPopup()

    configureSearchField(popup, popupConfig)

    PopupUtil.setPopupToggleComponent(popup, point.component)
    return popup.showAndAwaitSubmission(list, point)
  }

  suspend fun <T> showAsyncChooserPopup(point: RelativePoint,
                                        itemsLoader: suspend () -> List<T>,
                                        presenter: (T) -> PopupItemPresentation,
                                        popupConfig: PopupConfig = PopupConfig.DEFAULT): T? =
    showAsyncChooserPopup(point, itemsLoader, { presenter(it).shortText }, createSimpleItemRenderer(presenter), popupConfig)

  suspend fun <T> showAsyncChooserPopup(point: RelativePoint,
                                        itemsLoader: suspend () -> List<T>,
                                        filteringMapper: (T) -> String,
                                        renderer: ListCellRenderer<T>,
                                        popupConfig: PopupConfig = PopupConfig.DEFAULT): T? {
    val listModel = CollectionListModel<T>()
    val list = createList(listModel, renderer)
    val loadingListener = ListLoadingListener(listModel, itemsLoader, list)

    @Suppress("UNCHECKED_CAST")
    val popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setFilteringEnabled { filteringMapper(it as T) }
      .setResizable(true)
      .setMovable(true)
      .setFilterAlwaysVisible(popupConfig.alwaysShowSearchField)
      .addListener(loadingListener)
      .createPopup()

    configureSearchField(popup, popupConfig)

    PopupUtil.setPopupToggleComponent(popup, point.component)
    return popup.showAndAwaitSubmission(list, point)
  }


  suspend fun <T> JBPopup.showAndAwaitListSubmission(point: RelativePoint): T? {
    @Suppress("UNCHECKED_CAST")
    val list = UIUtil.findComponentOfType(content, JList::class.java) as JList<T>
    return showAndAwaitSubmission(list, point)
  }

  private suspend fun <T> JBPopup.showAndAwaitSubmission(list: JList<T>, point: RelativePoint): T? {
    try {
      show(point)
      return waitForChoiceAsync(list).await()
    }
    catch (e: CancellationException) {
      cancel()
      throw e
    }
  }

  /**
   * [PopupChooserBuilder.setItemChosenCallback] fires on every selection change
   * [PopupChooserBuilder.setCancelCallback] fires on every popup close
   * So we need a custom listener here
   */
  private fun <T> JBPopup.waitForChoiceAsync(list: JList<T>): Deferred<T> {
    val result = CompletableDeferred<T>(parent = null)
    addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        if (event.isOk) {
          val selectedValue = list.selectedValue
          result.complete(selectedValue)
        }
        else {
          result.cancel()
        }
      }
    })
    return result
  }

  private fun <T> createList(listModel: CollectionListModel<T>, renderer: ListCellRenderer<T>): JBList<T> =
    JBList(listModel).apply {
      visibleRowCount = 7
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      cellRenderer = renderer
      background = JBUI.CurrentTheme.Popup.BACKGROUND
    }

  private class ListLoadingListener<T>(private val listModel: CollectionListModel<T>,
                                       private val itemsLoader: suspend () -> List<T>,
                                       private val list: JBList<T>) : JBPopupListener {

    private var scope: CoroutineScope? = null

    override fun beforeShown(event: LightweightWindowEvent) {
      scope = MainScope().apply {
        launch {
          with(list) {
            startLoading()
            val items = itemsLoader()
            listModel.replaceAll(items)
            finishLoading()
          }
          event.asPopup().pack(true, true)
        }
      }
    }

    private fun JBList<T>.startLoading() {
      setPaintBusy(true)
      emptyText.text = ApplicationBundle.message("label.loading.page.please.wait")
    }

    private fun JBList<T>.finishLoading() {
      setPaintBusy(false)
      emptyText.text = UIBundle.message("message.noMatchesFound")
      if (selectedIndex == -1) {
        selectedIndex = 0
      }
    }

    override fun onClosed(event: LightweightWindowEvent) {
      scope?.cancel()
    }
  }

  interface PopupItemPresentation {
    val shortText: @Nls String
    val icon: Icon?
    val fullText: @Nls String?

    class Simple(override val shortText: String,
                 override val icon: Icon? = null,
                 override val fullText: String? = null)
      : PopupItemPresentation

    class ToString(value: Any) : PopupItemPresentation {
      override val shortText: String = value.toString()
      override val icon: Icon? = null
      override val fullText: String? = null
    }
  }

  interface SelectablePopupItemPresentation {
    val icon: Icon?
    val shortText: @Nls String
    val fullText: @Nls String?
    val isSelected: Boolean

    data class Simple(override val shortText: String,
                      override val icon: Icon? = null,
                      override val fullText: String? = null,
                      override val isSelected: Boolean = false) : SelectablePopupItemPresentation
  }

  fun <T> createSimpleItemRenderer(presenter: (T) -> PopupItemPresentation): ListCellRenderer<T> {
    val simplePopupItemRenderer = SimplePopupItemRenderer(presenter)
    if (!ExperimentalUI.isNewUI())
      return simplePopupItemRenderer

    simplePopupItemRenderer.ipad.left = 0
    simplePopupItemRenderer.ipad.right = 0
    return RoundedCellRenderer(simplePopupItemRenderer, false)
  }

  class SimplePopupItemRenderer<T>(private val presenter: (T) -> PopupItemPresentation) : ColoredListCellRenderer<T>() {
    init {
      iconTextGap = JBUIScale.scale(4)
    }

    override fun customizeCellRenderer(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
      val presentation = presenter(value)
      icon = presentation.icon
      append(presentation.shortText)
      val fullText = presentation.fullText
      if (fullText != null) {
        append(" ")
        append("$fullText", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }

      // ColoredListCellRenderer sets null for a background in case of !selected, so it can't work with SelectablePanel
      if (!selected) background = list.background
    }
  }

  private fun configureSearchField(popup: JBPopup, popupConfig: PopupConfig) {
    val searchTextField = UIUtil.findComponentOfType(popup.content, SearchTextField::class.java)
    if (searchTextField != null) {
      tuneSearchFieldForNewUI(searchTextField)
      setSearchFieldPlaceholder(searchTextField, popupConfig.searchTextPlaceHolder)
    }
  }

  private fun tuneSearchFieldForNewUI(searchTextField: SearchTextField) {
    if (!ExperimentalUI.isNewUI()) return
    AbstractPopup.customizeSearchFieldLook(searchTextField, true)
  }

  private fun setSearchFieldPlaceholder(searchTextField: SearchTextField, placeholderText: @NlsContexts.StatusText String?) {
    placeholderText ?: return
    searchTextField.textEditor.emptyText.text = placeholderText
    FragmentedSettingsUtil.setupPlaceholderVisibility(searchTextField.textEditor)
  }
}

data class PopupConfig(
  val alwaysShowSearchField: Boolean = true,
  val searchTextPlaceHolder: @NlsContexts.StatusText String? = null
) {
  companion object {
    val DEFAULT = PopupConfig()
  }
}

class SimpleSelectablePopupItemRenderer<T> private constructor(private val reviewerPresenter: (T) -> ChooserPopupUtil.SelectablePopupItemPresentation) : ListCellRenderer<T> {
  private val checkBox: JBCheckBox = JBCheckBox().apply {
    isOpaque = false
  }
  private val label: SimpleColoredComponent = SimpleColoredComponent().apply {
    iconTextGap = JBUIScale.scale(4)
  }
  private val panel = BorderLayoutPanel(6, 5).apply {
    addToLeft(checkBox)
    addToCenter(label)
    border = JBUI.Borders.empty(TOP_BOTTOM_GAP, LEFT_RIGHT_GAP)
  }

  override fun getListCellRendererComponent(list: JList<out T>,
                                            value: T,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val presentation = reviewerPresenter(value)

    checkBox.apply {
      this.isSelected = presentation.isSelected
      isFocusPainted = cellHasFocus
      isFocusable = cellHasFocus
    }

    label.apply {
      clear()
      append(presentation.shortText)
      icon = presentation.icon
      foreground = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
    }

    UIUtil.setBackgroundRecursively(panel, ListUiUtil.WithTallRow.background(list, isSelected, true))

    return panel
  }

  companion object {
    fun <T> create(presenter: (T) -> ChooserPopupUtil.SelectablePopupItemPresentation): ListCellRenderer<T> {
      val simplePopupItemRenderer = SimpleSelectablePopupItemRenderer(presenter)
      if (!ExperimentalUI.isNewUI())
        return simplePopupItemRenderer

      return RoundedCellRenderer(simplePopupItemRenderer, false)
    }

    private const val TOP_BOTTOM_GAP = 1
    private val LEFT_RIGHT_GAP: Int
      get() = CollaborationToolsUIUtil.getSize(oldUI = 5, newUI = 0) // in case of the newUI gap handled by SelectablePanel
  }
}
