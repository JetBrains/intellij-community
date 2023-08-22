// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl

import com.intellij.ide.impl.dataRules.GetDataRule
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.psi.PsiFile

class TargetSymbolsDataRule : GetDataRule {

  override fun getData(dataProvider: DataProvider): Any? {
    val file: PsiFile = CommonDataKeys.PSI_FILE.getData(dataProvider) ?: return null
    val offset = CommonDataKeys.CARET.getData(dataProvider)?.offset ?: return null
    return targetSymbols(file, offset).takeIf {
      it.isNotEmpty()
    }
  }
}
