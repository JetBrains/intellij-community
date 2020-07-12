// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName

import com.intellij.DefaultBundleService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.Experiments

class LocalizedActionAliasMatcher : GotoActionAliasMatcher {
  override fun match(action: AnAction, name: String): Boolean {
    if (Experiments.getInstance().isFeatureEnabled("i18n.match.actions").not()) {
      return false
    }
    return GotoActionItemProvider.buildMatcher(name).matches(DefaultBundleService.getInstance().compute {
      action.templatePresentation.textWithPossibleMnemonic.get().text
    })
  }
}