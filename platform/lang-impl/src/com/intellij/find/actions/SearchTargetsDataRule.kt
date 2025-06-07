// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions

import com.intellij.find.actions.FindUsagesAction.SEARCH_TARGETS
import com.intellij.find.usages.impl.symbolSearchTargets
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataRule

private class SearchTargetsDataRule : UiDataRule {

  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    sink.lazyValue(SEARCH_TARGETS) { dataProvider ->
      val project = dataProvider[CommonDataKeys.PROJECT] ?: return@lazyValue null
      val symbols = dataProvider[CommonDataKeys.SYMBOLS] ?: return@lazyValue null
      symbolSearchTargets(project, symbols).takeIf {
        it.isNotEmpty()
      }
    }
  }
}
