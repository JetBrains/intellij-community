// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightProducts

import com.intellij.application.options.editor.EditorOptionsProviderEP
import com.intellij.codeInsight.daemon.impl.GutterIntentionMenuContributor
import com.intellij.codeInsight.daemon.impl.IntentionMenuContributor
import com.intellij.ide.GeneralSettings
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.internal.statistic.service.fus.collectors.UsageCollectorBean
import com.intellij.internal.statistic.service.fus.collectors.UsageCollectors
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.unregisterEverything
import com.intellij.openapi.extensions.impl.unregisterExtensions
import com.intellij.openapi.extensions.impl.unregisterExtensionsById
import com.intellij.openapi.extensions.impl.unregisterExtensionsMatching
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

private val logger = Logger.getInstance("#com.intellij.ide.lightProducts.LightProductExtensionsCleanup")

/**
 * Unregisters application-level extensions that the JetBrains Client light session does not need, and disables tips on startup.
 *
 * The standalone JetBrains Light product does not call this function. It starts in `ProductMode.LIGHT_MONOLITH`,
 * and that mode leaves the backend modules out of the plugin set.
 * Product-specific deregistrations stay in `com.intellij.platform.frontend.split.baseline.core.ThinClientAppExtensionsConfigurator`.
 */
@ApiStatus.Internal
fun unregisterExtensionsForLightProduct(checkNotInstantiated: Boolean = false) {
  SearchEverywhereContributor.EP_NAME.appPoint
    .unregisterExtensions(
      FileSearchEverywhereContributorFactory::class,
      checkNotInstantiated = checkNotInstantiated,
    )

  ChooseByNameContributor.FILE_EP_NAME.appPoint.unregisterEverything(checkNotInstantiated)

  IntentionMenuContributor.EP_NAME.appPoint
    .unregisterExtensions(
      GutterIntentionMenuContributor::class,
      checkNotInstantiated = checkNotInstantiated,
    )

  UsageCollectors.PROJECT_EP_NAME.appPoint
    .unregisterExtensionsMatching(checkNotInstantiated) { _, adapter ->
      val bean = adapter.createInstance<UsageCollectorBean>(application)
      bean?.implementationClass != "com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector"
    }.also { reports ->
      if (reports.none { !it.wasUnregistered }) logger.warn("Can't keep FileTypeUsagesCollector - Not Found")
    }

  // The project-level settings pages are removed in `openProjectForLightProduct`.
  Configurable.APPLICATION_CONFIGURABLE.appPoint
    .unregisterExtensionsById(
      "ide.date.format",
      "editing.templates",
      "trusted.hosts",
      "diff.base",
      "editor.breadcrumbs",
      "editor.stickyLines",
      checkNotInstantiated = checkNotInstantiated,
    )

  EditorOptionsProviderEP.EP_NAME.appPoint
    .unregisterExtensionsById(
      "editor.preferences.smartKeys",
      "editor.preferences.completion",
      "reference.settingsdialog.IDE.editor.postfix.templates",
      checkNotInstantiated = checkNotInstantiated,
    )

  StatusBarWidgetFactory.EP_NAME.appPoint
    .unregisterExtensionsById(
      "VfsRefresh",
      "LineSeparator",
      "Encoding",
      "PowerSaveMode",
      "largeFileEncodingWidget",
      "CodeStyleStatusBarWidget",
      "ReadOnlyAttribute",
      "SmartModeIndicator",
      "IndexesAndVfsFlushIndicator",
      checkNotInstantiated = checkNotInstantiated,
    )

  GeneralSettings.getInstance().isShowTipsOnStartup = false
}

private val <T : Any> ExtensionPointName<T>.appPoint: ExtensionPoint<T> get() = application.extensionArea.getExtensionPoint(this)
