// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util.popup

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.ui.popup.util.RoundedCellRenderer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.util.childScope
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
    showChooserPopup(point, items, { presenter(it).shortText }, createSimpleItemRenderer(presenter), popupConfig)

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
      .createPopup()

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)

    PopupUtil.setPopupToggleComponent(popup, point.component)
    return popup.showAndAwaitSubmission(list, point)
  }

  @JvmOverloads
  suspend fun <T : Any> showAsyncChooserPopup(point: RelativePoint,
                                              itemsLoader: Flow<List<T>>,
                                              presenter: (T) -> PopupItemPresentation,
                                              popupConfig: PopupConfig = PopupConfig.DEFAULT): T? =
    showAsyncChooserPopup(point, itemsLoader, { presenter(it).shortText }, createSimpleItemRenderer(presenter), popupConfig)

  @JvmOverloads
  suspend fun <T : Any> showAsyncChooserPopup(point: RelativePoint,
                                              itemsLoader: Flow<List<T>>,
                                              filteringMapper: (T) -> String,
                                              renderer: ListCellRenderer<T>,
                                              popupConfig: PopupConfig = PopupConfig.DEFAULT): T? = coroutineScope {
    val listModel = CollectionListModel<T>()
    val list = createList(listModel, renderer)
    val loadingListener = ListLoadingListener(this, listModel, itemsLoader, list)

    @Suppress("UNCHECKED_CAST")
    val popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setFilteringEnabled { filteringMapper(it as T) }
      .setResizable(true)
      .setMovable(true)
      .setFilterAlwaysVisible(popupConfig.alwaysShowSearchField)
      .addListener(loadingListener)
      .createPopup()

    CollaborationToolsPopupUtil.configureSearchField(popup, popupConfig)

    PopupUtil.setPopupToggleComponent(popup, point.component)
    popup.showAndAwaitSubmission(list, point)
  }

  private fun <T> createList(listModel: CollectionListModel<T>, renderer: ListCellRenderer<T>): JBList<T> =
    JBList(listModel).apply {
      visibleRowCount = 7
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      cellRenderer = renderer
      background = JBUI.CurrentTheme.Popup.BACKGROUND
    }

  private class ListLoadingListener<T : Any>(private val parentScope: CoroutineScope,
                                             private val listModel: CollectionListModel<T>,
                                             private val items: Flow<List<T>>,
                                             private val list: JBList<T>) : JBPopupListener {
    private var cs: CoroutineScope? = null

    override fun beforeShown(event: LightweightWindowEvent) {
      val cs = parentScope.childScope()
      this.cs = cs

      cs.launchNow {
        items.catch { e ->
          val errorMessage = e.localizedMessage ?: CollaborationToolsBundle.message("popup.data.loading.error")
          list.emptyText.setText(errorMessage, SimpleTextAttributes.ERROR_ATTRIBUTES)
          LOG.error(e)
        }.collect {
          list.emptyText.clear()
          val selected = list.selectedIndex
          if (it.size > listModel.size) {
            val newList = it.subList(listModel.size, it.size)
            listModel.addAll(listModel.size, newList)
          }
          if (selected != -1) {
            list.selectedIndex = selected
          }
        }
      }
    }

    override fun onClosed(event: LightweightWindowEvent) {
      cs?.cancel()
      cs = null
    }
  }

  fun <T> createSimpleItemRenderer(presenter: (T) -> PopupItemPresentation): ListCellRenderer<T> {
    val simplePopupItemRenderer = SimplePopupItemRenderer(presenter)
    if (!ExperimentalUI.isNewUI())
      return simplePopupItemRenderer

    simplePopupItemRenderer.ipad.left = 0
    simplePopupItemRenderer.ipad.right = 0
    return RoundedCellRenderer(simplePopupItemRenderer, false)
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