// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName

import com.intellij.DynamicBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.util.DefaultBundleService
import com.intellij.util.ResourceUtil
import java.util.*

internal class LocalizedActionAliasMatcher : GotoActionAliasMatcher {
  override fun match(action: AnAction, name: String): Boolean {
    return DefaultBundleService.getInstance().compute { action.templatePresentation.textWithPossibleMnemonic.get().text }
             ?.let { GotoActionItemProvider.buildMatcher(name).matches(it) } ?: return false
  }
}

internal class DefaultBundleActionAliasMatcher : GotoActionAliasMatcher {
  override fun match(action: AnAction, name: String): Boolean {
    if (DynamicBundle.findLanguageBundle() == null) return false

    val id = ActionManager.getInstance().getId(action)
    val text = actions.value.getProperty("action.$id.text")
    val description = actions.value.getProperty("action.$id.description")

    val buildMatcher = GotoActionItemProvider.buildMatcher(name)

    if (text.isNullOrBlank() && description.isNullOrBlank()) return false
    if (!text.isNullOrBlank() && buildMatcher.matches(text)) return true
    if (!description.isNullOrBlank() && buildMatcher.matches(description)) return true

    return false
  }

  companion object {
    var actions = lazy {
      Properties().apply {
        load(ResourceUtil.getResourceAsStream(DefaultBundleActionAliasMatcher::class.java.classLoader, "com.intellij.ide.util.gotoByName",
                                                 "DefaultActionsBundle.properties"))
      }
    }
  }
}