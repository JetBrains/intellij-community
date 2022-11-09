// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.IdeBundle
import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.ui.OptionsSearchTopHitProvider.ApplicationLevelProvider
import com.intellij.ide.ui.OptionsSearchTopHitProvider.ProjectLevelProvider
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.WordPrefixMatcher
import com.intellij.util.text.Matcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.CancellationException
import java.util.function.Consumer
import kotlin.coroutines.coroutineContext

abstract class OptionsTopHitProvider : OptionsSearchTopHitProvider, SearchTopHitProvider {
  companion object {
    // project level here means not that EP itself in project area, but that extensions applicable for project only
    @JvmField
    val PROJECT_LEVEL_EP = ExtensionPointName<ProjectLevelProvider>("com.intellij.search.projectOptionsTopHitProvider")

    @JvmStatic
    fun consumeTopHits(provider: OptionsSearchTopHitProvider,
                       rawPattern: String,
                       collector: Consumer<Any>,
                       project: Project?) {
      val pattern = checkPattern(rawPattern) ?: return
      val parts = pattern.split(' ')
      if (!parts.isEmpty()) {
        doConsumeTopHits(provider, pattern, parts[0], collector, project)
      }
    }

    @VisibleForTesting
    @JvmStatic
    fun buildMatcher(pattern: String?): Matcher = WordPrefixMatcher(pattern)

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
    consumeTopHits(this, pattern, collector, project)
  }

  abstract override fun getId(): String

  /*
   * Marker interface for option provider containing only descriptors which are backed by toggle actions.
   * E.g. UiSettings.SHOW_STATUS_BAR is backed by View > Status Bar action.
   */
  @Deprecated("") // for search everywhere only
  interface CoveredByToggleActions

  // ours ProjectLevelProvider registered in ours projectOptionsTopHitProvider extension point,
  // not in common topHitProvider, so, this adapter is required to expose ours project level providers.
  internal class ProjectLevelProvidersAdapter : SearchTopHitProvider {
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
        doConsumeTopHits(provider, checkedPattern, parts[0], collector, project)
      }
    }

    fun consumeAllTopHits(pattern: String, collector: Consumer<Any>, project: Project?) {
      val matcher = buildMatcher(pattern)
      for (provider in PROJECT_LEVEL_EP.extensionList) {
        consumeTopHitsForApplicableProvider(provider, matcher, collector, project)
      }
    }
  }

  internal class Activity : ProjectPostStartupActivity {
    private val appJob: Job

    init {
      val app = ApplicationManager.getApplication()
      if (app.isUnitTestMode || app.isHeadlessEnvironment) {
        throw ExtensionNotApplicableException.create()
      }

      appJob = app.coroutineScope.launch {
        // for application
        cacheAll(null)
      }
    }

    override suspend fun execute(project: Project) {
      appJob.join()
      // for given project
      cacheAll(project)
    }
  }
}

private suspend fun cacheAll(project: Project?) {
  val name = if (project == null) "application" else "project"
  val activity = StartUpMeasurer.startActivity("cache options in $name", ActivityCategory.DEFAULT)
  SearchTopHitProvider.EP_NAME.processExtensions { provider, pluginDescriptor ->
    if (provider is OptionsSearchTopHitProvider && (project == null || provider !is ApplicationLevelProvider)) {
      val p = provider as OptionsSearchTopHitProvider
      if (p.preloadNeeded() && (project == null || !project.isDisposed)) {
        coroutineContext.ensureActive()
        getCachedOptions(p, project, pluginDescriptor)
      }
    }
  }

  coroutineContext.ensureActive()

  if (project != null) {
    val cache = ProjectTopHitCache.getInstance(project)
    OptionsTopHitProvider.PROJECT_LEVEL_EP.processExtensions { provider, pluginDescriptor ->
      coroutineContext.ensureActive()
      try {
        cache.getCachedOptions(provider, project, pluginDescriptor)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        if (e is ControlFlowException) {
          throw e
        }
        logger<OptionsTopHitProvider>().error(e)
      }
    }
  }
  activity.end()
}

private fun getCachedOptions(provider: OptionsSearchTopHitProvider,
                             project: Project?,
                             pluginDescriptor: PluginDescriptor?): Collection<OptionDescription> {
  val cache = if (project == null || provider is ApplicationLevelProvider) {
    TopHitCache.getInstance()
  }
  else {
    ProjectTopHitCache.getInstance(project)
  }
  return cache.getCachedOptions(provider, project, pluginDescriptor)
}

private fun doConsumeTopHits(provider: OptionsSearchTopHitProvider,
                             rawPattern: String,
                             id: String,
                             collector: Consumer<Any>,
                             project: Project?) {
  var pattern = rawPattern
  if (provider.id.startsWith(id) || pattern.startsWith(" ")) {
    pattern = (if (pattern.startsWith(' ')) pattern else pattern.substring(id.length)).trim()
    consumeTopHitsForApplicableProvider(provider, OptionsTopHitProvider.buildMatcher(pattern), collector, project)
  }
}

private fun consumeTopHitsForApplicableProvider(provider: OptionsSearchTopHitProvider,
                                                matcher: Matcher,
                                                collector: Consumer<Any>,
                                                project: Project?) {
  for (option in getCachedOptions(provider = provider, project = project, pluginDescriptor = null)) {
    if (matcher.matches(option.option)) {
      collector.accept(option)
    }
  }
}

private fun checkPattern(pattern: String): String? {
  return if (pattern.startsWith(SearchTopHitProvider.getTopHitAccelerator())) pattern.substring(1) else null
}