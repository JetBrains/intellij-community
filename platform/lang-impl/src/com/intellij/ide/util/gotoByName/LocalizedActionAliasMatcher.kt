// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.util.DefaultBundleService

internal class LocalizedActionAliasMatcher : GotoActionAliasMatcher {
  override fun matchAction(action: AnAction, name: String): MatchMode {
    val service = DefaultBundleService.getInstance()
    val matcher = GotoActionItemProvider.buildMatcher(name)

    val defaultText = service.compute { action.templatePresentation.textWithPossibleMnemonic.get().text }
    if (defaultText != null && matcher.matches(defaultText)) return MatchMode.NAME

    val defaultDescription = service.compute { action.templatePresentation.description }
    if (defaultDescription != null && matcher.matches(defaultDescription)) return MatchMode.DESCRIPTION

    if (action.synonyms.map { service.compute { it.get() } }.any { it != null && matcher.matches(it) }) return MatchMode.SYNONYM

    return MatchMode.NONE
  }
}