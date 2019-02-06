// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.openapi.util.registry.Registry

/**
 * Go To Declaration which doesn't invoke Show Usages if there are no declarations to go
 */
class GotoDeclarationOnlyAction : GotoDeclarationAction() {

  override fun getHandler(): CodeInsightActionHandler {
    return if (Registry.`is`("ide.goto.target")) GotoDeclarationOnlyHandler2 else GotoDeclarationOnlyHandler
  }
}
