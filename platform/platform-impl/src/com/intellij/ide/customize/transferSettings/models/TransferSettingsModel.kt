// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

import com.intellij.ide.customize.transferSettings.TransferSettingsConfiguration
import javax.swing.DefaultListModel

class TransferSettingsModel(private val config: TransferSettingsConfiguration) {
  val listModel = DefaultListModel<BaseIdeVersion>()

  fun performRefresh(selectionTargetId: String?): List<BaseIdeVersion> {
    // todo: might be calculated on other thread. but we don't need it now as operations are quite fast for now
    config.dataProvider.refresh()

    val newOrdered = config.dataProvider.orderedIdeVersions

    listModel.removeAllElements()
    listModel.addAll(newOrdered)

    return newOrdered
  }
}