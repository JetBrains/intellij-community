// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.lang.IdeLanguageCustomization
import com.intellij.navigation.ChooseByNameRegistry
import com.intellij.navigation.GotoClassContributor
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.Nls

object GotoClassPresentationUpdater {
  @JvmStatic
  fun getTabTitle(): String {
    val split = getActionTitle().split("/".toRegex()).take(2).toTypedArray()
    return split[0] + if (split.size > 1) " +" else ""
  }

  @JvmStatic
  @Nls
  fun getTabTitlePluralized(): String = getGotoClassContributor()?.tabTitlePluralized ?: IdeBundle.message("go.to.class.kind.text.pluralized")

  @JvmStatic
  fun getActionTitle(): String {
    return StringUtil.capitalizeWords(getGotoClassContributor()?.elementKind
                                      ?: IdeBundle.message("go.to.class.kind.text"), " /", true, true)
  }

  @JvmStatic
  @Nls
  fun getActionTitlePluralized(): List<String> {
    return (getGotoClassContributor()?.elementKindsPluralized ?: listOf(IdeBundle.message("go.to.class.kind.text.pluralized")))
  }

  @JvmStatic
  fun getElementKinds(): Set<String> {
    return getElementKinds { it.elementKind.split("/") }
  }

  @JvmStatic
  fun getElementKindsPluralized(): Set<String> {
    return getElementKinds { it.elementKindsPluralized }
  }

  private fun getGotoClassContributor(): GotoClassContributor? {
    return ChooseByNameRegistry.getInstance().classModelContributorList
      .asSequence()
      .filterIsInstance<GotoClassContributor>()
      .firstOrNull { it.elementLanguage in IdeLanguageCustomization.getInstance().primaryIdeLanguages }
  }

  private fun getElementKinds(transform: (GotoClassContributor) -> Iterable<String>): LinkedHashSet<String> {
    val primaryIdeLanguages = IdeLanguageCustomization.getInstance().primaryIdeLanguages
    return ChooseByNameRegistry.getInstance().classModelContributorList
      .asSequence()
      .filterIsInstance<GotoClassContributor>()
      .sortedBy {
        val index = primaryIdeLanguages.indexOf(it.elementLanguage)
        if (index == -1) primaryIdeLanguages.size else index
      }
      .flatMapTo(LinkedHashSet(), transform)
  }
}
