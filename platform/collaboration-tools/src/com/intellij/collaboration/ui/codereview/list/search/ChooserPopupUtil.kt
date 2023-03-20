// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.popup.*
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.PopupState
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

object ChooserPopupUtil {

  suspend fun <T> showChooserPopup(point: RelativePoint,
                                   popupState: PopupState<JBPopup>,
                                   items: List<T>,
                                   presenter: (T) -> PopupItemPresentation): T? =
    showChooserPopup(point, popupState, items, { presenter(it as T).shortText }, SimplePopupItemRenderer(presenter))

  suspend fun <T> showChooserPopup(point: RelativePoint,
                                   popupState: PopupState<JBPopup>,
                                   items: List<T>,
                                   filteringMapper: (T) -> String,
                                   renderer: ListCellRenderer<T>): T? {
    val listModel = CollectionListModel(items)
    val list = createList(listModel, renderer)

    @Suppress("UNCHECKED_CAST")
    val popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setFilteringEnabled { filteringMapper(it as T) }
      .setResizable(true)
      .setMovable(true)
      .setFilterAlwaysVisible(true)
      .createPopup()

    popupState.prepareToShow(popup)
    return popup.showAndAwaitSubmission(list, point)
  }

  suspend fun <T> showAsyncChooserPopup(point: RelativePoint,
                                        popupState: PopupState<JBPopup>,
                                        itemsLoader: suspend () -> List<T>,
                                        presenter: (T) -> PopupItemPresentation): T? {
    val listModel = CollectionListModel<T>()
    val list = createList(listModel, SimplePopupItemRenderer(presenter))
    val loadingListener = ListLoadingListener(listModel, itemsLoader, list)

    @Suppress("UNCHECKED_CAST")
    val popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setFilteringEnabled { presenter(it as T).shortText }
      .setResizable(true)
      .setMovable(true)
      .setFilterAlwaysVisible(true)
      .addListener(loadingListener)
      .createPopup()

    popupState.prepareToShow(popup)
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

  class SimplePopupItemRenderer<T>(private val presenter: (T) -> PopupItemPresentation) : ColoredListCellRenderer<T>() {
    override fun customizeCellRenderer(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
      val presentation = presenter(value)
      icon = presentation.icon
      append(presentation.shortText)
      val fullText = presentation.fullText
      if (fullText != null) {
        append(" ")
        append("($fullText)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }
}