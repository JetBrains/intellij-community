// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.impl.symbolSearchTargets
import com.intellij.ide.impl.dataRules.GetDataRule
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.IndexNotReadyException
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchTargetsDataRule : GetDataRule {

  override fun getData(dataProvider: DataProvider): Collection<SearchTarget>? {
    val project = CommonDataKeys.PROJECT.getData(dataProvider) ?: return null
    try {
      val symbols = CommonDataKeys.SYMBOLS.getData(dataProvider) ?: return null
      return symbolSearchTargets(project, symbols).takeIf {
        it.isNotEmpty()
      }
    }
    catch (e: IndexNotReadyException) {
      return null
    }
  }
}
