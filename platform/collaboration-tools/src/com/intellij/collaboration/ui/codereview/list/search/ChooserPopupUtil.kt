// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.details.SelectableWrapper
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.name
import com.intellij.collaboration.ui.util.popup.*
import com.intellij.openapi.ui.popup.*
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
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

object ChooserPopupUtil {

  @JvmOverloads
  suspend fun <T> showChooserPopup(
    point: RelativePoint,
    items: List<T>,
    presenter: (T) -> PopupItemPresentation,
    popupConfig: PopupConfig = PopupConfig.DEFAULT,
  ): T? =
    showChooserPopup(
      point = point,
      items = items,
      filteringMapper = { presenter(it).shortText },
      renderer = SimplePopupItemRenderer.create(presenter),
      popupConfig = popupConfig,
    )

  @JvmOverloads
  suspend fun <T> showChooserPopup(
    point: RelativePoint,
    items: List<T>,
    filteringMapper: (T) -> String,
    renderer: ListCellRenderer<T>,
    popupConfig: PopupConfig = PopupConfig.DEFAULT,
  ): T? {
    val listModel = CollectionListModel(items)
    val list = createList(listModel, renderer)

    @Suppress("UNCHECKED_CAST")
    val popup = PopupChooserBuilder(list)
      .apply {
        setFilteringEnabled { filteringMapper(it as T) }
        configure(popupConfig)
      }
      .createPopup()

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)

    PopupUtil.setPopupToggleComponent(popup, point.component)
    return popup.showAndAwaitSubmission(list, point, popupConfig.showDirection)
  }

  // Async choosers:

  @JvmOverloads
  suspend fun <T : Any> showAsyncChooserPopup(
    point: RelativePoint,
    itemsLoader: Flow<Result<List<T>>>,
    presenter: (T) -> PopupItemPresentation,
    popupConfig: PopupConfig = PopupConfig.DEFAULT,
  ): T? =
    showAsyncChooserPopup(
      point = point,
      itemsLoader = itemsLoader,
      filteringMapper = { presenter(it).shortText },
      renderer = SimplePopupItemRenderer.create(presenter),
      popupConfig = popupConfig,
    )

  @JvmOverloads
  suspend fun <T : Any> showAsyncChooserPopup(
    point: RelativePoint,
    itemsLoader: Flow<Result<List<T>>>,
    filteringMapper: (T) -> String,
    renderer: ListCellRenderer<T>,
    popupConfig: PopupConfig = PopupConfig.DEFAULT,
  ): T? = coroutineScope {
    val listModel = CollectionListModel<T>()
    val list = createList(listModel, renderer)
    val loadingListener = ListLoadingListener(this, itemsLoader, list, listModel, popupConfig.errorPresenter)

    @Suppress("UNCHECKED_CAST")
    val popup = PopupChooserBuilder(list)
      .setFilteringEnabled { filteringMapper(it as T) }
      .addListener(loadingListener)
      .configure(popupConfig)
      .createPopup()

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)

    PopupUtil.setPopupToggleComponent(popup, point.component)
    popup.showAndAwaitSubmission(list, point, popupConfig.showDirection)
  }

  @JvmOverloads
  suspend fun <T : Any> showAsyncChooserPopup(
    point: RelativePoint,
    presenter: (T) -> PopupItemPresentation,
    popupConfig: PopupConfig = PopupConfig.DEFAULT,
    renderer: ListCellRenderer<T> = SimplePopupItemRenderer.create(presenter),
    configure: CoroutineScope.(JBPopup, JBList<T>, CollectionListModel<T>, JScrollPane) -> Unit,
  ): T? = coroutineScope {
    val listModel = CollectionListModel<T>()
    val list = createList(listModel, renderer)
    fun filteringMapper(item: T) = presenter(item).shortText

    @Suppress("UNCHECKED_CAST")
    val popupBuilder = PopupChooserBuilder(list)
      .setFilteringEnabled { filteringMapper(it as T) }
      .configure(popupConfig)
    val popup = popupBuilder.createPopup()

    configure(popup, list, listModel, popupBuilder.scrollPane)

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)

    PopupUtil.setPopupToggleComponent(popup, point.component)
    popup.showAndAwaitSubmission(list, point, popupConfig.showDirection)
  }

  // Multiple options:

  @ApiStatus.Internal
  @JvmOverloads
  suspend fun <T : Any> showAsyncMultipleChooserPopup(
    point: RelativePoint,
    loadedBatchesFlow: Flow<Result<List<T>>>,
    presenter: (T) -> PopupItemPresentation,
    isOriginallySelected: (T) -> Boolean,
    popupConfig: PopupConfig = PopupConfig.DEFAULT,
  ): List<T> = coroutineScope {
    val listModel = CollectionListModel<SelectableWrapper<T>>()
    val list = createSelectableList(listModel, SimpleSelectablePopupItemRenderer.create { item ->
      SelectablePopupItemPresentation.fromPresenter(presenter, item)
    })
    val selectableBatchesFlow = loadedBatchesFlow.map { result ->
      result.map { list ->
        list.map {
          SelectableWrapper(it, isOriginallySelected(it))
        }
      }
    }
    val loadingListener = ListLoadingListener(
      this, selectableBatchesFlow, list, listModel, popupConfig.errorPresenter
    )

    @Suppress("UNCHECKED_CAST")
    val popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setFilteringEnabled { selectableItem ->
        selectableItem as SelectableWrapper<T>
        presenter(selectableItem.value).shortText
      }
      .setCloseOnEnter(false)
      .addListener(loadingListener)
      .configure(popupConfig)
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
    renderer: ListCellRenderer<SelectableWrapper<T>>,
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

  private fun <T> PopupChooserBuilder<T>.configure(popupConfig: PopupConfig): PopupChooserBuilder<T> {
    val builder = this

    val title = popupConfig.title
    if (title != null) builder.setTitle(title)

    builder.setFilterAlwaysVisible(popupConfig.alwaysShowSearchField)
    builder.setMovable(popupConfig.isMovable)
    builder.setResizable(popupConfig.isResizable)

    return builder
  }
}

data class PopupConfig(
  val title: @NlsContexts.PopupTitle String? = null,
  val searchTextPlaceHolder: @NlsContexts.StatusText String? = null,
  val alwaysShowSearchField: Boolean = true,
  val isMovable: Boolean = true,
  val isResizable: Boolean = true,
  val showDirection: ShowDirection = ShowDirection.BELOW,
  val errorPresenter: ErrorStatusPresenter.Text<Throwable>? = null,
) {
  companion object {
    val DEFAULT = PopupConfig()
  }
}

enum class ShowDirection {
  ABOVE,
  BELOW
}

private class ListLoadingListener<T>(
  private val parentScope: CoroutineScope,
  private val itemsFlow: Flow<Result<List<T>>>,
  private val list: JBList<T>,
  private val listModel: CollectionListModel<T>,
  private val errorPresenter: ErrorStatusPresenter.Text<Throwable>?,
) : JBPopupListener {
  private var cs: CoroutineScope? = null

  override fun beforeShown(event: LightweightWindowEvent) {
    val cs = parentScope.childScope()
    this.cs = cs

    cs.launchNow {
      list.setPaintBusy(true)
      list.emptyText.clear()
      try {
        itemsFlow.collect { resultedItems ->
          resultedItems.fold(
            onSuccess = { items -> onSuccess(items) },
            onFailure = { exception ->
              showErrorOnPopupFailure(exception, errorPresenter, list)
            }
          )
        }
      }
      finally {
        list.setPaintBusy(false)
      }
    }
  }

  private fun onSuccess(items: List<T>) {
    val selected = list.selectedIndex
    if (items.size > listModel.size) {
      val newList = items.subList(listModel.size, items.size)
      listModel.addAll(listModel.size, newList)
    }
    if (selected != -1) {
      list.selectedIndex = selected
    }
  }

  override fun onClosed(event: LightweightWindowEvent) {
    cs?.cancel()
    cs = null
  }
}

private fun showErrorOnPopupFailure(e: Throwable, errorPresenter: ErrorStatusPresenter.Text<Throwable>?, list: JBList<*>) {
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