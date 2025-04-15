// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl

import com.intellij.openapi.actionSystem.*

private class TargetSymbolsDataRule : UiDataRule {
  @Suppress("StaticInitializationInExtensions")
  companion object {
    init {
      InjectedDataKeys.injectedKey(CommonDataKeys.SYMBOLS)
    }
  }

  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    sink.lazyValue(CommonDataKeys.SYMBOLS) { dataProvider ->
      val file = dataProvider[CommonDataKeys.PSI_FILE] ?: return@lazyValue null
      val offset = dataProvider[CommonDataKeys.CARET]?.offset ?: return@lazyValue null
      targetSymbols(file, offset).takeIf {
        it.isNotEmpty()
      }
        ?.toList()
    }
  }
}
