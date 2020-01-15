// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.ui.PopupMenuListenerAdapter
import javax.swing.event.PopupMenuEvent

class SdkComboBox(model: SdkComboBoxModel) : SdkComboBoxBase<SdkListItem>(model.modelBuilder) {
  val model get() = getModel() as SdkComboBoxModel

  override fun onModelUpdated(listModel: SdkListModel) {
    setModel(model.copyAndSetListModel(listModel))
  }

  override fun setSelectedItem(anObject: Any?) {
    if (anObject is SdkListItem) {
      if (myModel.executeAction(this, anObject, ::setSelectedItem)) {
        return
      }
    }
    reloadModel()
    super.setSelectedItem(anObject)
  }

  fun setSelectedSdk(sdk: Sdk?) {
    reloadModel()
    val sdkItem = sdk?.let { model.listModel.findSdkItem(sdk) }
    selectedItem = when {
      sdk == null -> showNoneSdkItem()
      sdkItem == null -> showInvalidSdkItem(sdk.name)
      else -> sdkItem
    }
  }

  fun getSelectedSdk(): Sdk? {
    return when (val it = selectedItem) {
      is SdkListItem.ProjectSdkItem -> model.sdksModel.projectSdk
      is SdkListItem.SdkItem -> it.sdk
      else -> null
    }
  }

  init {
    setModel(model)
    setRenderer(SdkListPresenter { this@SdkComboBox.model.listModel })
    addPopupMenuListener(ModelReloadProvider())
    reloadModel()
  }

  private inner class ModelReloadProvider : PopupMenuListenerAdapter() {
    private var disposable: Disposable? = null

    override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
      val disposable = Disposer.newDisposable()
      setReloadDisposable(disposable)
      myModel.reloadActions()
      myModel.detectItems(this@SdkComboBox, disposable)
    }

    override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
      setReloadDisposable(null)
    }

    private fun setReloadDisposable(parentDisposable: Disposable?) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      parentDisposable?.let { Disposer.register(model.project, it) }
      disposable?.let { Disposer.dispose(it) }
      disposable = parentDisposable
    }
  }
}