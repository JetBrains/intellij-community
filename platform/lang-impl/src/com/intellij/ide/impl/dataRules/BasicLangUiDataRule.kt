// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules

import com.intellij.openapi.actionSystem.CommonDataKeys.*
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.InjectedDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys.PASTE_TARGET_PSI_ELEMENT
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.MODULE
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.PSI_ELEMENT_ARRAY
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.usages.UsageView.USAGE_INFO_LIST_KEY
import com.intellij.usages.UsageView.USAGE_TARGETS_KEY

private class BasicLangUiDataRule: UiDataRule {
  @Suppress("StaticInitializationInExtensions")
  companion object {
    init {
      InjectedDataKeys.injectedKey(USAGE_TARGETS_KEY)
    }
  }

  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    sink.lazyValue(PSI_FILE) { dataProvider ->
      PsiFileRule.getData(dataProvider)
    }
    sink.lazyValue(PSI_ELEMENT) { dataProvider ->
      PsiElementFromSelectionRule.getData(dataProvider)
    }
    sink.lazyValue(PSI_ELEMENT_ARRAY) { dataProvider ->
      PsiElementFromSelectionsRule.getData(dataProvider)
    }
    sink.lazyValue(PASTE_TARGET_PSI_ELEMENT) { dataProvider ->
      PasteTargetRule.getData(dataProvider)
    }
    sink.lazyValue(VIRTUAL_FILE) {
      VirtualFileRule.getData(it)
    }
    sink.lazyValue(VIRTUAL_FILE_ARRAY) {
      VirtualFileArrayRule.getData(it)
    }
    sink.lazyValue(NAVIGATABLE) {
      NavigatableRule.getData(it)
    }
    sink.lazyValue(USAGE_TARGETS_KEY) {
      UsageTargetsRule.getData(it)
    }
    sink.lazyValue(USAGE_INFO_LIST_KEY) {
      UsageInfo2ListRule.getData(it)
    }
    sink.lazyValue(MODULE) {
      ModuleRule.getData(it)
    }
  }
}