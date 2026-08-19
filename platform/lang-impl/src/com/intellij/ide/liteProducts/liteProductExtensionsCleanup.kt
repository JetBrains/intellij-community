// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.liteProducts

import com.intellij.codeInsight.daemon.impl.GutterIntentionMenuContributor
import com.intellij.codeInsight.daemon.impl.IntentionMenuContributor
import com.intellij.find.impl.TextSearchContributor
import com.intellij.ide.GeneralSettings
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.internal.statistic.service.fus.collectors.UsageCollectorBean
import com.intellij.internal.statistic.service.fus.collectors.UsageCollectors
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.unregisterEverything
import com.intellij.openapi.extensions.impl.unregisterEverythingExcept
import com.intellij.openapi.extensions.impl.unregisterExtensions
import com.intellij.openapi.extensions.impl.unregisterExtensionsById
import com.intellij.openapi.extensions.impl.unregisterExtensionsMatching
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.psi.search.FileTypeIndexImpl
import com.intellij.psi.stubs.StubIndexExtension
import com.intellij.usages.impl.UsageGroupingRuleProviderImpl
import com.intellij.usages.rules.UsageGroupingRuleProvider
import com.intellij.util.application
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.IndexableSetContributor
import org.jetbrains.annotations.ApiStatus

private val logger = Logger.getInstance("#com.intellij.ide.liteProducts.LiteProductExtensionsCleanup")

/**
 * Unregisters application-level extensions that are not needed in lightweight IDE products
 * (the JetBrains Client frontend and JetBrains Light), and disables tips on startup.
 *
 * Product-specific deregistrations stay in the product configurators,
 * see `com.intellij.platform.frontend.split.base.core.ThinClientAppExtensionsConfigurator`
 * and `com.intellij.idea.ultimate.light.customization.IjLightAppExtensionsConfigurator`.
 */
@ApiStatus.Internal
fun unregisterExtensionsForLiteProduct() {
  UsageGroupingRuleProvider.EP_NAME.appPoint
    .unregisterExtensions(
      UsageGroupingRuleProviderImpl::class
    )

  SearchEverywhereContributor.EP_NAME.appPoint
    .unregisterExtensions(
      ClassSearchEverywhereContributor.Factory::class,
      SymbolSearchEverywhereContributor.Factory::class,
      FileSearchEverywhereContributorFactory::class,
      TextSearchContributor.Companion.Factory::class,
    )

  ChooseByNameContributor.FILE_EP_NAME.appPoint.unregisterEverything()

  IntentionMenuContributor.EP_NAME.appPoint
    .unregisterExtensions(
      GutterIntentionMenuContributor::class
    )

  FileBasedIndexExtension.EXTENSION_POINT_NAME.appPoint
    .unregisterEverythingExcept(
      // Unregistering of this extension leads to "Index is not created for `filetypes`" errors
      FileTypeIndexImpl::class
    )

  IndexableSetContributor.EP_NAME.appPoint.unregisterEverything()
  StubIndexExtension.EP_NAME.appPoint.unregisterEverything()

  UsageCollectors.PROJECT_EP_NAME.appPoint
    .unregisterExtensionsMatching { _, adapter ->
      val bean = adapter.createInstance<UsageCollectorBean>(application)
      bean?.implementationClass != "com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector"
    }.also { reports ->
      if (reports.none { !it.wasUnregistered }) logger.warn("Can't keep FileTypeUsagesCollector - Not Found")
    }

  ToolWindowEP.EP_NAME.appPoint
    .unregisterExtensionsById(
      ToolWindowId.BOOKMARKS,
      "Dependencies",
    )

  Configurable.APPLICATION_CONFIGURABLE.appPoint
    .unregisterExtensionsById(
      "ide.date.format",
      "editing.templates",
      "trusted.hosts",
      "diff.base",
    )

  StatusBarWidgetFactory.EP_NAME.appPoint
    .unregisterExtensionsById(
      "VfsRefresh",
      "LanguageServiceStatusBarWidget",
      "LineSeparator",
      "Encoding",
      "PowerSaveMode",
      "largeFileEncodingWidget",
      "CodeStyleStatusBarWidget",
      "ReadOnlyAttribute",
      "inspectionProfileWidget",
      "SmartModeIndicator",
      "IndexesAndVfsFlushIndicator",
    )

  GeneralSettings.getInstance().isShowTipsOnStartup = false
}

private val <T : Any> ExtensionPointName<T>.appPoint: ExtensionPoint<T> get() = application.extensionArea.getExtensionPoint(this)
