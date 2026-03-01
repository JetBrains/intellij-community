// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.name
import com.intellij.collaboration.ui.util.popup.CollaborationToolsPopupUtil
import com.intellij.collaboration.ui.util.popup.PopupItemPresentation
import com.intellij.collaboration.ui.util.popup.SelectablePopupItemPresentation.Simple
import com.intellij.collaboration.ui.util.popup.SimplePopupItemRenderer
import com.intellij.collaboration.ui.util.popup.SimpleSelectablePopupItemRenderer
import com.intellij.collaboration.ui.util.popup.showAndAwaitSubmission
import com.intellij.collaboration.ui.util.popup.showAndAwaitSubmissions
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.collaboration.util.onNoValue
import com.intellij.collaboration.util.onValueAvailable
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JScrollPane
import javax.swing.ListCellRenderer
import javax.swing.ListModel
import javax.swing.ListSelectionModel

/**
 * Utility object for displaying chooser popups with various loading mechanisms.
 *
 * This utility supports both preloaded item display and dynamically loaded item display,
 * with options for customization in appearance and filtering behavior.
 */
object ChooserPopupUtil {

  /**
   * Shows a chooser popup with preloaded items.
   *
   * @param point the point at which to show the popup
   * @param items the complete list of items to display
   * @param presenter a function that creates a [PopupItemPresentation] for each item
   * @param popupConfig configuration options for the popup appearance and behavior
   * @return the selected item, or `null` if the popup was cancelled
   */
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
      filteringMapper = filterByNamesFromPresentation(presenter),
      renderer = SimplePopupItemRenderer.create(presenter),
      popupConfig = popupConfig,
    )

  /**
   * Shows a chooser popup with preloaded items.
   *
   * @param point the point at which to show the popup
   * @param items the complete list of items to display
   * @param filteringMapper a function that extracts a filterable string from each item
   * @param renderer the cell renderer for displaying items in the list
   * @param popupConfig configuration options for the popup appearance and behavior
   * @return the selected item, or `null` if the popup was cancelled
   */
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

  /**
   * Shows a chooser popup that loads items progressively.
   *
   * The [itemsLoader] flow must emit **accumulated lists** on each emission.
   * Each emission should contain all items loaded so far, not just the new items.
   *
   * Example of correct usage:
   * ```
   * // Emission 1: [A, B, C]
   * // Emission 2: [A, B, C, D, E, F]  // includes previous items
   * // Emission 3: [A, B, C, D, E, F, G, H, I]  // includes all items
   * ```
   *
   * @param point the point at which to show the popup
   * @param itemsLoader a flow that emits accumulated lists of items wrapped in [Result]
   * @param presenter a function that creates a [PopupItemPresentation] for each item
   * @param popupConfig configuration options for the popup appearance and behavior
   * @return the selected item, or `null` if the popup was cancelled
   */
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
      filteringMapper = filterByNamesFromPresentation(presenter),
      renderer = SimplePopupItemRenderer.create(presenter),
      popupConfig = popupConfig,
    )

  /**
   * Shows a chooser popup that loads items progressively.
   *
   * The [itemsLoader] flow must emit **accumulated lists** on each emission.
   * Each emission should contain all items loaded so far, not just the new items.
   *
   * Example of correct usage:
   * ```
   * // Emission 1: [A, B, C]
   * // Emission 2: [A, B, C, D, E, F]  // includes previous items
   * // Emission 3: [A, B, C, D, E, F, G, H, I]  // includes all items
   * ```
   *
   * @param point the point at which to show the popup
   * @param itemsLoader a flow that emits accumulated lists of items wrapped in [Result]
   * @param filteringMapper a function that extracts a filterable string from each item
   * @param renderer the cell renderer for displaying items in the list
   * @param popupConfig configuration options for the popup appearance and behavior
   * @return the selected item, or `null` if the popup was cancelled
   */
  @JvmOverloads
  suspend fun <T : Any> showAsyncChooserPopup(
    point: RelativePoint,
    itemsLoader: Flow<Result<List<T>>>,
    filteringMapper: (T) -> String,
    renderer: ListCellRenderer<T>,
    popupConfig: PopupConfig = PopupConfig.DEFAULT,
  ): T? {
    val listModel = CollectionListModel<T>()
    val list = createList(listModel, renderer)
    list.launchOnShow("List items loader") {
      list.setPaintBusy(true)
      list.emptyText.clear()
      try {
        itemsLoader.collect { resultedItems ->
          resultedItems.fold(
            onSuccess = { items ->
              val selected = list.selectedIndex
              if (items.size > listModel.size) {
                val newList = items.subList(listModel.size, items.size)
                listModel.addAll(listModel.size, newList)
              }
              if (selected != -1) {
                list.selectedIndex = selected
              }
            },
            onFailure = { exception ->
              list.emptyText.showError(exception, popupConfig.errorPresenter)
            }
          )
        }
      }
      finally {
        list.setPaintBusy(false)
      }
    }

    @Suppress("UNCHECKED_CAST")
    val popup = PopupChooserBuilder(list)
      .setFilteringEnabled { filteringMapper(it as T) }
      .configure(popupConfig)
      .createPopup()

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)
    PopupUtil.setPopupToggleComponent(popup, point.component)

    return popup.showAndAwaitSubmission<T>(list, point, popupConfig.showDirection)
  }

  /**
   * Shows a chooser popup with custom configuration.
   *
   * This overload provides direct access to the popup components for custom loading logic.
   * The caller is responsible for populating the [CollectionListModel] via the [configure] callback.
   *
   * @param point the point at which to show the popup
   * @param presenter a function that creates a [PopupItemPresentation] for each item
   * @param popupConfig configuration options for the popup appearance and behavior
   * @param renderer the cell renderer for displaying items in the list
   * @param configure a callback that receives the popup components for custom configuration
   * @return the selected item, or `null` if the popup was cancelled
   */
  @ApiStatus.Internal
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

    popupBuilder.setCancelOnOtherWindowOpen(false)
    popupBuilder.setCancelOnWindowDeactivation(false)

    val popup = popupBuilder.createPopup()

    configure(popup, list, listModel, popupBuilder.scrollPane)

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)

    PopupUtil.setPopupToggleComponent(popup, point.component)
    popup.showAndAwaitSubmission(list, point, popupConfig.showDirection)
  }

  /**
   * Displays a chooser popup with incremental loading of items, allowing the user to select an item
   * from a dynamically updated list. The list is managed via a state flow, and supports filtering and custom rendering.
   *
   * @param point A reference to the relative point where the popup will be displayed.
   * @param listState A [StateFlow] containing the state of the incrementally computed list of items.
   * @param presenter A function that takes a list item and returns its presentation details,
   *                  such as a short text, an icon, or a full description.
   * @param popupConfig Configuration options for the popup, including its behavior, appearance, and additional features.
   *                    Defaults to [PopupConfig.DEFAULT].
   * @return The user-selected item from the list, or null if no item is selected.
   */
  @JvmOverloads
  suspend fun <T : Any> showChooserPopupWithIncrementalLoading(
    point: RelativePoint,
    listState: StateFlow<IncrementallyComputedValue<List<T>>>,
    presenter: (T) -> PopupItemPresentation,
    popupConfig: PopupConfig = PopupConfig.DEFAULT,
  ): T? {
    val listModel = CollectionListModel<T>()
    val list = createList(listModel, SimplePopupItemRenderer.create(presenter))
    list.launchOnShow("List items loader") {
      listState.collect { state ->
        list.setPaintBusy(state.isLoading)

        state.exceptionOrNull?.let { exception ->
          list.emptyText.showError(exception, popupConfig.errorPresenter) // TODO: show error even when list is not empty
        } ?: run {
          list.emptyText.clear()
        }

        state.onNoValue {
          listModel.removeAll()
        }.onValueAvailable { newList ->
          val selected = list.selectedValue
          listModel.replaceAll(newList) // TODO: optimal update via com.intellij.util.diff.Diff.buildChanges
          list.setSelectedValue(selected, true)
        }
      }
    }
    @Suppress("UNCHECKED_CAST")
    val popup = PopupChooserBuilder(list)
      .setFilteringEnabled { presenter(it as T).shortText }
      .configure(popupConfig)
      .createPopup()

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)
    PopupUtil.setPopupToggleComponent(popup, point.component)
    return popup.showAndAwaitSubmission(list, point, popupConfig.showDirection)
  }

  // Multiple options:

  /**
   * Displays a chooser popup that allows selecting multiple items from a list, with support for incremental loading.
   *
   * @param point The location where the popup will be shown as a relative point.
   * @param currentItems The list of currently available items that should be initially displayed in the chooser.
   * @param listState A [StateFlow] representing the state of incrementally loaded values for the items in the popup.
   *                  It provides new items, tracks loading status, and contains any exceptions encountered.
   * @param presenter A function that maps each item in the list to a [PopupItemPresentation], which determines how the
   *                  item will appear in the popup (e.g., text, icon).
   * @param popupConfig A [PopupConfig] to configure the popup's behavior and appearance, with a default
   *                    configuration provided if not specified.
   * @return A list of items selected by the user from the popup.
   */
  @ApiStatus.Internal
  @JvmOverloads
  suspend fun <T : Any> showMultipleChooserPopupWithIncrementalLoading(
    point: RelativePoint,
    currentItems: List<T>,
    listState: StateFlow<IncrementallyComputedValue<List<T>>>,
    presenter: (T) -> PopupItemPresentation,
    popupConfig: PopupConfig = PopupConfig.DEFAULT,
  ): List<T> {
    val listModel = MultiChooserListModel<T>().apply {
      add(currentItems)
      setChosen(currentItems)
    }
    val list = createSelectableList(listModel, presenter)

    list.launchOnShow("List items loader") {
      listState.collect { state ->
        list.setPaintBusy(state.isLoading)

        state.exceptionOrNull?.let { exception ->
          list.emptyText.showError(exception, popupConfig.errorPresenter) // TODO: show error even when list is not empty
        } ?: run {
          list.emptyText.clear()
        }

        state.onNoValue {
          listModel.removeAllExceptChosen()
        }.onValueAvailable { newList ->
          val selected = list.selectedValue
          listModel.retainChosenAndUpdate(newList)
          list.setSelectedValue(selected, true)
        }
      }
    }

    @Suppress("UNCHECKED_CAST")
    val popup = PopupChooserBuilder(list)
      .setFilteringEnabled {
        filterByNamesFromPresentation(presenter)(it as T)
      }
      .setCloseOnEnter(false)
      .configure(popupConfig)
      .createPopup()

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)
    PopupUtil.setPopupToggleComponent(popup, point.component)

    return popup.showAndAwaitSubmissions(listModel, point, popupConfig.showDirection)
  }

  /**
   * Displays an asynchronous popup allowing users to select multiple items from a dynamically loaded list.
   *
   * @param T The type of the items in the popup.
   * @param point The screen coordinate where the popup will be displayed.
   * @param selectedItems A list of items that will be pre-selected when the popup is shown.
   * @param loadedBatchesFlow A flow providing results of item batches to be displayed in the list.
   * @param presenter A function that maps each item to its presentation details, such as text and icon.
   * @param popupConfig Configuration options for the popup, including dimensions, behavior, and error handling. Defaults to `PopupConfig.DEFAULT`.
   * @return A list of items the user has selected.
   */
  @ApiStatus.Internal
  @JvmOverloads
  suspend fun <T : Any> showAsyncMultipleChooserPopup(
    point: RelativePoint,
    selectedItems: List<T>,
    loadedBatchesFlow: Flow<Result<List<T>>>,
    presenter: (T) -> PopupItemPresentation,
    popupConfig: PopupConfig = PopupConfig.DEFAULT,
  ): List<T> {
    val listModel = MultiChooserListModel<T>().apply {
      add(selectedItems)
      setChosen(selectedItems)
    }
    val list = createSelectableList(listModel, presenter)
    list.launchOnShow("List items loader") {
      list.setPaintBusy(true)
      list.emptyText.clear()
      try {
        loadedBatchesFlow.collect { resultedItems ->
          resultedItems.fold(
            onSuccess = { items ->
              val selected = list.selectedIndex
              if (items.size > listModel.size) {
                val newList = items.subList(listModel.size, items.size)
                listModel.add(newList)
              }
              if (selected != -1) {
                list.selectedIndex = selected
              }
            },
            onFailure = { exception ->
              list.emptyText.showError(exception, popupConfig.errorPresenter)
            }
          )
        }
      }
      finally {
        list.setPaintBusy(false)
      }
    }

    @Suppress("UNCHECKED_CAST")
    val popup = PopupChooserBuilder(list)
      .setFilteringEnabled { selectableItem ->
        presenter(selectableItem as T).shortText
      }
      .setCloseOnEnter(false)
      .configure(popupConfig)
      .createPopup().apply {
        // non-empty list returns pref size without considering "visibleRowCount"
        content.preferredSize = JBDimension(250, 300)
      }

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)
    PopupUtil.setPopupToggleComponent(popup, point.component)

    return popup.showAndAwaitSubmissions(listModel, point, popupConfig.showDirection)
  }

  private fun <T> createList(listModel: ListModel<T>, renderer: ListCellRenderer<T>): JBList<T> =
    JBList(listModel).apply {
      visibleRowCount = 7
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      cellRenderer = renderer
      background = JBUI.CurrentTheme.Popup.BACKGROUND
    }

  private fun <T> createSelectableList(
    chooserModel: MultiChooserListModel<T>,
    presenter: (T) -> PopupItemPresentation,
  ): JBList<T> {
    val rendererWithChooser = SimpleSelectablePopupItemRenderer.create<T> { item ->
      val presentation = presenter(item)
      Simple(
        presentation.shortText,
        presentation.icon,
        presentation.fullText,
        chooserModel.isChosen(item),
      )
    }
    return createList(chooserModel, rendererWithChooser).apply {
      fun toggleSelectedChosen() {
        val selectedIndex = selectedIndex.takeIf { it >= 0 } ?: return
        val selectedItem = model.getElementAt(selectedIndex) ?: return
        // model is wrapped for filtering, so we can't use the index directly
        chooserModel.toggleChosen(selectedItem)
        // due to a bug in FilteringListModel the changed item is not redrawn on change, so we have to do it manually
        getCellBounds(selectedIndex, selectedIndex)?.let { repaint(it) }
      }

      addMouseListener(object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent) {
          if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !UIUtil.isSelectionButtonDown(e) && !e.isConsumed) {
            toggleSelectedChosen()
          }
        }
      })
      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent?) {
          if (e != null && e.keyCode == KeyEvent.VK_ENTER) {
            toggleSelectedChosen()
          }
        }
      })
    }
  }

  private fun <T> PopupChooserBuilder<T>.configure(popupConfig: PopupConfig): PopupChooserBuilder<T> {
    val builder = this

    val title = popupConfig.title
    if (title != null) builder.setTitle(title)

    builder.setFilterAlwaysVisible(popupConfig.alwaysShowSearchField)
    builder.setMovable(popupConfig.isMovable)
    builder.setResizable(popupConfig.isResizable)

    builder.setAutoPackHeightOnFiltering(popupConfig.isAutoPackHeightOnFiltering)

    return builder
  }

  private fun <T> filterByNamesFromPresentation(presenter: (T) -> PopupItemPresentation): (T) -> String = {
    buildString {
      val presentation = presenter(it)
      append(presentation.shortText)
      if (presentation.fullText != null) {
        append(" ${presentation.fullText}")
      }
    }
  }
}

/**
 * @param isAutoPackHeightOnFiltering If turned on, the popup height will automatically be minimized when a filter is installed
 * and when the number of items in the list changes. This should be turned off for popups that are shown above some point to
 * prevent the popup getting detached from the anchorpoint.
 */
data class PopupConfig(
  val title: @NlsContexts.PopupTitle String? = null,
  val searchTextPlaceHolder: @NlsContexts.StatusText String? = null,
  val alwaysShowSearchField: Boolean = true,
  val isMovable: Boolean = true,
  val isResizable: Boolean = true,
  val isAutoPackHeightOnFiltering: Boolean = false,
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

private fun StatusText.showError(e: Throwable, errorPresenter: ErrorStatusPresenter.Text<Throwable>?) {
  if (errorPresenter == null) {
    val errorMessage = e.localizedMessage ?: CollaborationToolsBundle.message("popup.data.loading.error")
    setText(errorMessage, SimpleTextAttributes.ERROR_ATTRIBUTES)
  }
  else {
    val errorAction = errorPresenter.getErrorAction(e)!!
    appendText(errorPresenter.getErrorTitle(e), SimpleTextAttributes.ERROR_ATTRIBUTES)
    appendSecondaryText(errorAction.name!!, SimpleTextAttributes.LINK_ATTRIBUTES, errorAction)
  }
}