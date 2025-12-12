// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.IdeBundle
import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.ui.OptionsSearchTopHitProvider.ApplicationLevelProvider
import com.intellij.ide.ui.OptionsSearchTopHitProvider.ProjectLevelProvider
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.PlatformKeyboardLayoutConverter
import com.intellij.psi.codeStyle.WordPrefixMatcher
import com.intellij.util.text.Matcher
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Consumer

@Internal
abstract class OptionsTopHitProvider : OptionsSearchTopHitProvider, SearchTopHitProvider {
  companion object {
    // project level here means not that EP itself in the project area, but that extension applicable for a project only
    @JvmField
    val PROJECT_LEVEL_EP: ExtensionPointName<ProjectLevelProvider> = ExtensionPointName("com.intellij.search.projectOptionsTopHitProvider")

    fun consumeTopHits(
      provider: OptionsSearchTopHitProvider,
      rawPattern: String,
      collector: (OptionDescription) -> Unit,
      project: Project?,
    ) {
      val pattern = checkPattern(rawPattern) ?: return
      val parts = pattern.split(' ')
      if (!parts.isEmpty()) {
        doConsumeTopHits(provider = provider, rawPattern = pattern, id = parts[0], collector = collector, project = project)
      }
    }

    @VisibleForTesting
    fun buildMatcher(pattern: String): Matcher = WordPrefixMatcher(pattern, PlatformKeyboardLayoutConverter)

    @JvmStatic
    fun messageApp(property: @PropertyKey(resourceBundle = ApplicationBundle.BUNDLE) String): @Nls String {
      return StringUtil.stripHtml(ApplicationBundle.message(property), false)
    }

    @JvmStatic
    fun messageIde(property: @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String): @Nls String {
      return StringUtil.stripHtml(IdeBundle.message(property), false)
    }
  }

  override fun consumeTopHits(pattern: String, collector: Consumer<Any>, project: Project?) {
    consumeTopHits(provider = this, rawPattern = pattern, collector = { collector.accept(it) }, project = project)
  }

  abstract override fun getId(): String

  /*
   * Marker interface for option provider containing only descriptors which are backed by toggle actions.
   * E.g., UiSettings.SHOW_STATUS_BAR is backed by View > Status Bar action.
   */
  @Deprecated("") // for search everywhere only
  interface CoveredByToggleActions

  // ours ProjectLevelProvider registered in ours projectOptionsTopHitProvider extension point,
  // not in common topHitProvider, so, this adapter is required to expose ours project level providers.
  @Internal
  class ProjectLevelProvidersAdapter : SearchTopHitProvider {
    override fun consumeTopHits(pattern: String, collector: Consumer<Any>, project: Project?) {
      if (project == null) {
        return
      }

      val checkedPattern = checkPattern(pattern) ?: return
      val parts = checkedPattern.split(' ')
      if (parts.isEmpty()) {
        return
      }

      for (provider in PROJECT_LEVEL_EP.extensionList) {
        doConsumeTopHits(
          provider = provider,
          rawPattern = checkedPattern,
          id = parts[0],
          collector = { collector.accept(it) },
          project = project,
        )
      }
    }

    fun blockingConsumeAllTopHits(pattern: String, collector: (OptionDescription) -> Unit, project: Project?) {
      val matcher = buildMatcher(pattern)
      for (provider in PROJECT_LEVEL_EP.extensionList) {
        consumeTopHitsForApplicableProvider(provider = provider, matcher = matcher, collector = collector, project = project)
      }
    }

    suspend fun consumeAllTopHits(pattern: String, collector: suspend (OptionDescription) -> Unit, project: Project?) {
      val matcher = buildMatcher(pattern)
      var appLevelCache: TopHitCache? = null
      var projectLevelCache: TopHitCache? = null
      for (provider in PROJECT_LEVEL_EP.extensionList) {
        val cache = if (project == null || provider is ApplicationLevelProvider) {
          appLevelCache ?: TopHitCache.getInstanceAsync().also { appLevelCache = it }
        }
        else {
          projectLevelCache ?: TopHitCache.getInstanceAsync(project).also { projectLevelCache = it }
        }
        for (option in cache.getCachedOptions(provider = provider, project = project, pluginDescriptor = null)) {
          val optionValue = option.option ?: continue
          if (matcher.matches(optionValue)) {
            collector(option)
          }
        }
      }
    }
  }
}

private fun doConsumeTopHits(provider: OptionsSearchTopHitProvider,
                             rawPattern: String,
                             id: String,
                             collector: (OptionDescription) -> Unit,
                             project: Project?) {
  var pattern = rawPattern
  if (provider.id.startsWith(id) || pattern.startsWith(" ")) {
    pattern = (if (pattern.startsWith(' ')) pattern else pattern.substring(id.length)).trim()
    consumeTopHitsForApplicableProvider(
      provider = provider,
      matcher = OptionsTopHitProvider.buildMatcher(pattern),
      collector = collector,
      project = project,
    )
  }
}

private fun consumeTopHitsForApplicableProvider(
  provider: OptionsSearchTopHitProvider,
  matcher: Matcher,
  collector: (OptionDescription) -> Unit,
  project: Project?,
) {
  val cache = if (project == null || provider is ApplicationLevelProvider) {
    TopHitCache.getInstance()
  }
  else {
    TopHitCache.getInstance(project = project)
  }
  for (option in cache.getCachedOptions(provider = provider, project = project, pluginDescriptor = null)) {
    val optionValue = option.option ?: continue
    if (matcher.matches(optionValue)) {
      collector(option)
    }
  }
}

private fun checkPattern(pattern: String): String? {
  return if (pattern.startsWith(SearchTopHitProvider.getTopHitAccelerator())) pattern.substring(1) else null
}