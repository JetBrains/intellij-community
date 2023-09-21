// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser.actions

import com.intellij.icons.AllIcons
import com.intellij.importSettings.data.ActionsDataProvider
import com.intellij.importSettings.data.JBrActionsDataProvider
import com.intellij.importSettings.data.Product
import com.intellij.importSettings.data.SyncActionsDataProvider
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction

class OtherOptions(val callback: (Int) -> Unit) : ProductChooserAction(), LinkAction {
  private val jbDataProvider = JBrActionsDataProvider.getInstance()
  private val syncDataProvider = SyncActionsDataProvider.getInstance()

  private var jb: List<AnAction>? = null
  private var sync: List<AnAction>? = null
  private val config = ConfigAction(callback)

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val jbProducts = jbDataProvider.other
    val syncProducts = syncDataProvider.other

    val arr = mutableListOf<AnAction>()
    if (jb == null && jbProducts != null) {
      jb = addActionList(jbProducts, jbDataProvider, "Installed")
    }

    if (sync == null && syncProducts != null) {
      sync = addActionList(syncProducts, syncDataProvider, "Setting Sync")
    }

    jb?.let {
      if (it.isNotEmpty()) {
        arr.addAll(it)
      }
    }

    sync?.let {
      if (it.isNotEmpty()) {
        arr.addAll(it)
      }
    }


    if (arr.isNotEmpty()) {
      arr.add(Separator())
    }
    arr.add(config)

    return arr.toTypedArray()
  }

  private fun addActionList(products: List<Product>, provider: ActionsDataProvider, title: String? = null): MutableList<AnAction> {
    val list = mutableListOf<AnAction>()
    if (products.isNotEmpty()) {
      title?.let {
        list.add(Separator(it))
      }
      list.addAll(productsToActions(products, provider, callback))
    }
    return list
  }

  init {
    templatePresentation.isPopupGroup = true
  }

  override fun update(e: AnActionEvent) {
    if (jb?.isNotEmpty() == true || sync?.isNotEmpty() == true) {
      e.presentation.isVisible = true
      e.presentation.text = "Other Options"
      e.presentation.icon = null
      e.presentation.isPopupGroup = true
    }
  }
}


