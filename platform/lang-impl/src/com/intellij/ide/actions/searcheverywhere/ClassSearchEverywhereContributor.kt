// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.actions.GotoClassPresentationUpdater.getActionTitlePluralized
import com.intellij.ide.actions.GotoClassPresentationUpdater.getTabTitlePluralized
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFiltersStatisticsCollector.LangFilterCollector
import com.intellij.ide.actions.searcheverywhere.footer.createPsiExtendedInfo
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.ide.util.gotoByName.GotoClassSymbolConfiguration
import com.intellij.ide.util.gotoByName.LanguageRef
import com.intellij.ide.util.gotoByName.LanguageRef.Companion.forAllLanguages
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.util.regex.Pattern

private val patternToDetectMembers = Pattern.compile("(.+)(#)(.*)")
private val LOG = logger<ClassSearchEverywhereContributor>()

@Deprecated("The old Search Everywhere is being sunset in favor of the new (Split) Search Everywhere (com.intellij.platform.searchEverywhere).")
open class ClassSearchEverywhereContributor @Internal constructor(event: AnActionEvent, contributorModules: List<SearchEverywhereContributorModule>?)
  : AbstractGotoSEContributor(event, contributorModules), EssentialContributor, SearchEverywherePreviewProvider {
  private val filter = createLanguageFilter(event.getRequiredData(CommonDataKeys.PROJECT))

  @Internal
  override val navigationHandler: SearchEverywhereNavigationHandler = ClassSearchEverywhereNavigationHandler(project)

  constructor(event: AnActionEvent) : this(event, null)

  @Internal
  companion object {
    @JvmStatic
    fun createLanguageFilter(project: Project): PersistentSearchEverywhereContributorFilter<LanguageRef> {
      val items = forAllLanguages()
      val persistentConfig = GotoClassSymbolConfiguration.getInstance(project)
      return PersistentSearchEverywhereContributorFilter(items, persistentConfig, LanguageRef::displayName, LanguageRef::icon)
    }
  }

  override fun getGroupName(): @Nls String = getTabTitlePluralized()

  override fun getFullGroupName(): String = getActionTitlePluralized().joinToString("/")

  override fun getSortWeight(): Int = 100

  @ApiStatus.Internal
  override fun createModelWithOperationDisposable(project: Project, operationDisposable: Disposable?): FilteringGotoByModel<LanguageRef> {
    val customModel = contributorModules?.firstNotNullOfOrNull { mod -> mod.createCustomModel(project, this, operationDisposable) }
    if (customModel != null) return customModel

    val model = GotoClassModel2(project)
    model.setFilterItems(filter.selectedElements)
    return model
  }

  override fun createModel(project: Project): FilteringGotoByModel<LanguageRef> {
    val customModel = contributorModules?.firstNotNullOfOrNull { mod -> mod.createCustomModel(project, this, null) }
    if (customModel != null) return customModel

    val model = GotoClassModel2(project)
    model.setFilterItems(filter.selectedElements)
    return model
  }

  override fun getActions(onChanged: Runnable): List<AnAction> {
    return doGetActions(filter = filter, statisticsCollector = LangFilterCollector(), onChanged = onChanged)
  }

  override fun filterControlSymbols(pattern: String): String {
    var effectivePattern = pattern
    if (effectivePattern.contains('#')) {
      effectivePattern = applyPatternFilter(effectivePattern, patternToDetectMembers)
    }
    if (effectivePattern.contains('$')) {
      effectivePattern = applyPatternFilter(effectivePattern, ChooseByNamePopup.patternToDetectAnonymousClasses)
    }

    return super.filterControlSymbols(effectivePattern)
  }

  override fun isEmptyPatternSupported(): Boolean = true

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getElementPriority(element: Any, searchPattern: String): Int {
    return super.getElementPriority(element, searchPattern) + 5
  }

  override fun createExtendedInfo(): ExtendedInfo? = createPsiExtendedInfo().let {
    contributorModules?.firstNotNullOfOrNull { mod -> mod.mixinExtendedInfo(it) } ?: it
  }

  @Internal
  class Factory : SearchEverywhereContributorFactory<Any?> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any?> {
      return PSIPresentationBgRendererWrapper.wrapIfNecessary(ClassSearchEverywhereContributor(initEvent))
    }

    override fun isAvailable(project: Project): Boolean {
      // The welcome-screen project has no source, so the contributor can never return a result.
      return !WelcomeScreenProjectProvider.isWelcomeScreenProject(project) &&
             GotoContributorsAvailabilityService.hasLocalClassContributors(project)
    }
  }
}