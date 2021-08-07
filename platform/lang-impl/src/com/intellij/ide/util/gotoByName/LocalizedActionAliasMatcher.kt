// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName

import com.intellij.DynamicBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.DefaultBundleService
import java.util.*

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

internal class DefaultBundleActionAliasMatcher : GotoActionAliasMatcher {
  override fun matchAction(action: AnAction, name: String): MatchMode {
    if (DynamicBundle.findLanguageBundle() == null) return MatchMode.NONE

    val id = ActionManager.getInstance().getId(action)
    val bundle = actions.value

    var text: String? = null
    if (bundle?.containsKey("action.$id.text") == true) {
      text = bundle.getString("action.$id.text")
    }

    var description: String? = null
    if (bundle?.containsKey("action.$id.description") == true ) {
      description = bundle.getString("action.$id.description")
    }

    val buildMatcher = GotoActionItemProvider.buildMatcher(name)

    if (text.isNullOrBlank() && description.isNullOrBlank()) return MatchMode.NONE
    if (!text.isNullOrBlank() && buildMatcher.matches(text)) return MatchMode.NAME
    if (!description.isNullOrBlank() && buildMatcher.matches(description)) return MatchMode.DESCRIPTION

    return MatchMode.NONE
  }

  companion object {
    val LOG = logger<DefaultBundleActionAliasMatcher>()

    var actions = lazy {
      try {
        ResourceBundle.getBundle("defaultBundleActions.DefaultActionsBundle", Locale.getDefault(),
                                 DynamicBundle.findLanguageBundle()?.pluginDescriptor?.pluginClassLoader ?: return@lazy null)
      }
      catch (e: Exception) {
        LOG.error(e)
        null
      }
    }
  }
}