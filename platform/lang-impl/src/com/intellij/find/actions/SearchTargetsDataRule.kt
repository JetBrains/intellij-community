// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions

import com.intellij.find.usages.impl.searchTargets
import com.intellij.ide.impl.dataRules.GetDataRule
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider

class SearchTargetsDataRule : GetDataRule {

  override fun getData(dataProvider: DataProvider): Any? {
    val file = CommonDataKeys.PSI_FILE.getData(dataProvider) ?: return null
    val offset = CommonDataKeys.CARET.getData(dataProvider)?.offset ?: return null
    return searchTargets(file, offset)
  }
}
