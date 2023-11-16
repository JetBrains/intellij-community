// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util.popup

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.details.SelectableWrapper
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.name
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

private val LOG: Logger
  get() = logger<ChooserPopupUtil>()

object ChooserPopupUtil {

  @JvmOverloads
  suspend fun <T> showChooserPopup(point: RelativePoint,
                                   items: List<T>,
                                   presenter: (T) -> PopupItemPresentation,
                                   popupConfig: PopupConfig = PopupConfig.DEFAULT): T? =
    showChooserPopup(point, items, { presenter(it).shortText }, SimplePopupItemRenderer.create(presenter), popupConfig)

  @JvmOverloads
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
      .also { builder ->
        val title = popupConfig.title ?: return@also
        builder.setTitle(title)
      }
      .createPopup()

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)

    PopupUtil.setPopupToggleComponent(popup, point.component)
    return popup.showAndAwaitSubmission(list, point, popupConfig.showDirection)
  }

  @JvmOverloads
  suspend fun <T : Any> showAsyncChooserPopup(point: RelativePoint,
                                              itemsLoader: Flow<Result<List<T>>>,
                                              presenter: (T) -> PopupItemPresentation,
                                              popupConfig: PopupConfig = PopupConfig.DEFAULT): T? =
    showAsyncChooserPopup(point, itemsLoader, { presenter(it).shortText }, SimplePopupItemRenderer.create(presenter), popupConfig)

  @JvmOverloads
  suspend fun <T : Any> showAsyncChooserPopup(point: RelativePoint,
                                              itemsLoader: Flow<Result<List<T>>>,
                                              filteringMapper: (T) -> String,
                                              renderer: ListCellRenderer<T>,
                                              popupConfig: PopupConfig = PopupConfig.DEFAULT): T? = coroutineScope {
    val listModel = CollectionListModel<T>()
    val list = createList(listModel, renderer)
    val loadingListener = ListLoadingListener(this, itemsLoader, list, listModel, popupConfig.errorPresenter)

    @Suppress("UNCHECKED_CAST")
    val popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setFilteringEnabled { filteringMapper(it as T) }
      .setResizable(true)
      .setMovable(true)
      .setFilterAlwaysVisible(popupConfig.alwaysShowSearchField)
      .addListener(loadingListener)
      .also { builder ->
        val title = popupConfig.title ?: return@also
        builder.setTitle(title)
      }
      .createPopup()

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)

    PopupUtil.setPopupToggleComponent(popup, point.component)
    popup.showAndAwaitSubmission(list, point, popupConfig.showDirection)
  }

  @JvmOverloads
  suspend fun <T : Any> showAsyncMultipleChooserPopup(
    point: RelativePoint,
    itemsLoader: Flow<Result<List<T>>>,
    presenter: (T) -> PopupItemPresentation,
    isOriginallySelected: (T) -> Boolean,
    popupConfig: PopupConfig = PopupConfig.DEFAULT
  ): List<T> = coroutineScope {
    val listModel = CollectionListModel<SelectableWrapper<T>>()
    val list = createSelectableList(listModel, SimpleSelectablePopupItemRenderer.create { item ->
      SelectablePopupItemPresentation.fromPresenter(presenter, item)
    })
    val loadingListener = SelectableListLoadingListener(
      this, itemsLoader, list, listModel, isOriginallySelected, popupConfig.errorPresenter
    )

    @Suppress("UNCHECKED_CAST")
    val popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setFilteringEnabled { selectableItem ->
        selectableItem as SelectableWrapper<T>
        presenter(selectableItem.value).shortText
      }
      .setCloseOnEnter(false)
      .setResizable(true)
      .setMovable(true)
      .setFilterAlwaysVisible(popupConfig.alwaysShowSearchField)
      .addListener(loadingListener)
      .also { builder ->
        val title = popupConfig.title ?: return@also
        builder.setTitle(title)
      }
      .createPopup()

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)
    PopupUtil.setPopupToggleComponent(popup, point.component)
    popup.showAndAwaitSubmissions(list, point, popupConfig.showDirection)
  }

  private fun <T> createList(listModel: CollectionListModel<T>, renderer: ListCellRenderer<T>): JBList<T> =
    JBList(listModel).apply {
      visibleRowCount = 7
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      cellRenderer = renderer
      background = JBUI.CurrentTheme.Popup.BACKGROUND
    }

  private fun <T> createSelectableList(
    listModel: CollectionListModel<SelectableWrapper<T>>,
    renderer: ListCellRenderer<SelectableWrapper<T>>
  ): JBList<SelectableWrapper<T>> = JBList(listModel).apply {
    visibleRowCount = 7
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = renderer
    background = JBUI.CurrentTheme.Popup.BACKGROUND
    addMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !UIUtil.isSelectionButtonDown(e) && !e.isConsumed)
          toggleSelection()
      }
    })
  }

  private fun <T> JList<SelectableWrapper<T>>.toggleSelection() {
    for (item in selectedValuesList) {
      item.isSelected = !item.isSelected
    }
    repaint()
  }
}

data class PopupConfig(
  val title: @NlsContexts.PopupTitle String? = null,
  val searchTextPlaceHolder: @NlsContexts.StatusText String? = null,
  val alwaysShowSearchField: Boolean = true,
  val showDirection: ShowDirection = ShowDirection.BELOW,
  val errorPresenter: ErrorStatusPresenter<Throwable>? = null
) {
  companion object {
    val DEFAULT = PopupConfig()
  }
}

enum class ShowDirection {
  ABOVE,
  BELOW
}

private abstract class AbstractListLoadingListener<T>(
  private val parentScope: CoroutineScope,
  private val itemsFlow: Flow<Result<List<T>>>,
  private val list: JBList<*>,
) : JBPopupListener {
  private var cs: CoroutineScope? = null

  abstract fun onSuccess(items: List<T>)
  abstract fun onFailure(exception: Throwable)

  override fun beforeShown(event: LightweightWindowEvent) {
    val cs = parentScope.childScope()
    this.cs = cs

    cs.launchNow {
      itemsFlow.collect { resultedItems ->
        list.emptyText.clear()
        resultedItems.fold(
          onSuccess = { items -> onSuccess(items) },
          onFailure = { exception -> onFailure(exception) }
        )
      }
    }
  }

  override fun onClosed(event: LightweightWindowEvent) {
    cs?.cancel()
    cs = null
  }
}

private class ListLoadingListener<T : Any>(
  private val parentScope: CoroutineScope,
  private val itemsFlow: Flow<Result<List<T>>>,
  private val list: JBList<*>,
  private val listModel: CollectionListModel<T>,
  private val errorPresenter: ErrorStatusPresenter<Throwable>?
) : AbstractListLoadingListener<T>(parentScope, itemsFlow, list) {
  override fun onSuccess(items: List<T>) {
    val selected = list.selectedIndex
    if (items.size > listModel.size) {
      val newList = items.subList(listModel.size, items.size)
      listModel.addAll(listModel.size, newList)
    }
    if (selected != -1) {
      list.selectedIndex = selected
    }
  }

  override fun onFailure(exception: Throwable) {
    showErrorOnPopupFailure(exception, errorPresenter, list)
  }
}

private class SelectableListLoadingListener<T : Any>(
  private val parentScope: CoroutineScope,
  private val itemsFlow: Flow<Result<List<T>>>,
  private val list: JBList<*>,
  private val listModel: CollectionListModel<SelectableWrapper<T>>,
  private val isOriginallySelected: (T) -> Boolean,
  private val errorPresenter: ErrorStatusPresenter<Throwable>?
) : AbstractListLoadingListener<T>(parentScope, itemsFlow, list) {
  override fun onSuccess(items: List<T>) {
    val newList = items.map { item -> SelectableWrapper(item, isOriginallySelected(item)) }
    listModel.replaceAll(newList)

    if (list.selectedIndex == -1) {
      list.selectedIndex = 0
    }
  }

  override fun onFailure(exception: Throwable) {
    showErrorOnPopupFailure(exception, errorPresenter, list)
  }
}

private fun showErrorOnPopupFailure(e: Throwable, errorPresenter: ErrorStatusPresenter<Throwable>?, list: JBList<*>) {
  if (errorPresenter == null) {
    val errorMessage = e.localizedMessage ?: CollaborationToolsBundle.message("popup.data.loading.error")
    list.emptyText.setText(errorMessage, SimpleTextAttributes.ERROR_ATTRIBUTES)
  }
  else {
    val errorAction = errorPresenter.getErrorAction(e)!!
    list.emptyText.appendText(errorPresenter.getErrorTitle(e), SimpleTextAttributes.ERROR_ATTRIBUTES)
    list.emptyText.appendSecondaryText(errorAction.name!!, SimpleTextAttributes.LINK_ATTRIBUTES, errorAction)
  }
}