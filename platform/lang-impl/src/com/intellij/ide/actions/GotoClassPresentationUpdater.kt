// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.lang.IdeLanguageCustomization
import com.intellij.navigation.ChooseByNameRegistry
import com.intellij.navigation.GotoClassContributor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.text.StringUtil

class GotoClassPresentationUpdater : PreloadingActivity() {
  override fun preload(indicator: ProgressIndicator) {
    //we need to change the template presentation to show the proper text for the action in Settings | Keymap
    val presentation = ActionManager.getInstance().getAction("GotoClass").templatePresentation
    presentation.text = StringUtil.capitalize(getMainElementKind()) + "..."
    presentation.description = IdeBundle.message("go.to.class.action.description", getElementKinds().joinToString("/"))
  }

  companion object {
    @JvmStatic
    fun getMainElementKind(): String {
      val mainIdeLanguage = IdeLanguageCustomization.getInstance().mainIdeLanguage
      val mainContributor = ChooseByNameRegistry.getInstance().classModelContributors
                              .filterIsInstance(GotoClassContributor::class.java)
                              .firstOrNull { mainIdeLanguage != null && mainIdeLanguage.`is`(it.elementLanguage) }
      return mainContributor?.elementKind ?: IdeBundle.message("go.to.class.kind.text")
    }

    private fun getElementKinds(): LinkedHashSet<String> {
      val mainIdeLanguage = IdeLanguageCustomization.getInstance().mainIdeLanguage
      return ChooseByNameRegistry.getInstance().classModelContributors
        .filterIsInstance(GotoClassContributor::class.java)
        .sortedBy { if (mainIdeLanguage != null && mainIdeLanguage.`is`(it.elementLanguage)) 0 else 1 }
        .mapTo(LinkedHashSet()) { it.elementKind }
    }
  }
}
