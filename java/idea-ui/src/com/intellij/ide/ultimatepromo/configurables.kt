// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ultimatepromo

import com.intellij.icons.AllIcons
import com.intellij.ide.JavaUiBundle
import com.intellij.java.ui.icons.JavaUIIcons
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.reflect.KClass

internal abstract class UltimatePromoConfigurable : ConfigurableWithId, Configurable.Promo {
  override fun isModified(): Boolean = false
  override fun apply() = Unit
  override fun getPromoIcon(): Icon = AllIcons.Ultimate.Lock
}

internal abstract class UltimateConfigurableProvider(private val clazz: KClass<out Configurable>) : ConfigurableProvider() {
  final override fun createConfigurable(): Configurable? {
    return clazz.java.getConstructor().newInstance()
  }

  final override fun canCreateConfigurable(): Boolean {
    return Registry.`is`("idea.ultimate.features.hints.enabled")
  }
}

private fun featurePage(@NlsContexts.Label title: String, items: List<PromoFeatureListItem>, pluginId: String): JComponent {
  return PromoPages.buildWithTryUltimate(
    PromoFeaturePage(
      JavaUIIcons.IdeaUltimatePromo,
      PluginAdvertiserService.ideaUltimate,
      title,
      items,
      FeaturePromoBundle.message("free.trial.hint"),
      pluginId
    )
  )
}

internal class PromoDatabaseConfigurableProvider : UltimateConfigurableProvider(PromoDatabaseConfigurable::class)
internal class PromoKubernetesConfigurableProvider : UltimateConfigurableProvider(PromoKubernetesConfigurable::class)
internal class PromoProfilerConfigurableProvider : UltimateConfigurableProvider(PromoProfilerConfigurable::class)
internal class PromoJSConfigurableProvider : UltimateConfigurableProvider(PromoJSConfigurable::class)
internal class PromoTSConfigurableProvider : UltimateConfigurableProvider(PromoTSConfigurable::class)
internal class PromoSwaggerConfigurableProvider : UltimateConfigurableProvider(PromoSwaggerConfigurable::class)

internal class PromoDatabaseConfigurable : UltimatePromoConfigurable() {
  override fun getId(): String = "promo.database"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.database")

  override fun createComponent(): JComponent {
    return featurePage(
      FeaturePromoBundle.message("feature.database.description.html", "https://www.jetbrains.com/help/idea/relational-databases.html"),
      listOf(
        PromoFeatureListItem(AllIcons.Nodes.DataTables, FeaturePromoBundle.message("feature.database.create.and.manage")),
        PromoFeatureListItem(AllIcons.Actions.Run_anything, FeaturePromoBundle.message("feature.database.run")),
        PromoFeatureListItem(AllIcons.ToolbarDecorator.Import, FeaturePromoBundle.message("feature.database.export"))
      ),
      "com.intellij.database"
    )
  }
}

internal class PromoKubernetesConfigurable : UltimatePromoConfigurable() {
  override fun getId(): String = "promo.kubernetes"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.kubernetes")

  override fun createComponent(): JComponent {
    return featurePage(
      FeaturePromoBundle.message("feature.kubernetes.description.html", "https://www.jetbrains.com/help/idea/kubernetes.html"),
      listOf(
        PromoFeatureListItem(AllIcons.Nodes.Deploy, FeaturePromoBundle.message("feature.kubernetes.deploy")),
        PromoFeatureListItem(AllIcons.Nodes.Console, FeaturePromoBundle.message("feature.kubernetes.logs")),
        PromoFeatureListItem(AllIcons.FileTypes.Properties, FeaturePromoBundle.message("feature.kubernetes.editor"))
      ),
      "com.intellij.kubernetes"
    )
  }
}

internal class PromoProfilerConfigurable : UltimatePromoConfigurable() {
  override fun getId(): String = "promo.profiler"
  override fun getDisplayName(): String = JavaUiBundle.message("promo.configurable.profiler")

  override fun createComponent(): JComponent {
    return featurePage(
      JavaUiBundle.message("feature.profiler.description.html", "https://www.jetbrains.com/help/idea/profiler-intro.html"),
      listOf(
        PromoFeatureListItem(AllIcons.Actions.ProfileCPU, JavaUiBundle.message("feature.profiler.cpu")),
        PromoFeatureListItem(AllIcons.Actions.ProfileMemory, JavaUiBundle.message("feature.profiler.memory")),
        PromoFeatureListItem(AllIcons.Actions.ProfileRed, JavaUiBundle.message("feature.profiler.hints"))
      ),
      "com.intellij.LineProfiler"
    )
  }
}

private fun javascriptFeaturePage(): JComponent {
  @Suppress("DialogTitleCapitalization")
  return featurePage(
    FeaturePromoBundle.message("feature.javascript.description.html",
                               "https://www.jetbrains.com/help/idea/javascript-specific-guidelines.html"),
    listOf(
      PromoFeatureListItem(AllIcons.Actions.ReformatCode, FeaturePromoBundle.message("feature.javascript.code")),
      PromoFeatureListItem(AllIcons.Actions.SuggestedRefactoringBulb, FeaturePromoBundle.message("feature.javascript.refactor")),
      PromoFeatureListItem(AllIcons.FileTypes.UiForm, FeaturePromoBundle.message("feature.javascript.frameworks"))
    ),
    "JavaScript"
  )
}

internal class PromoJSConfigurable : UltimatePromoConfigurable() {
  override fun getId(): String = "promo.javascript"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.javascript")
  override fun createComponent(): JComponent = javascriptFeaturePage()
}

internal class PromoTSConfigurable : UltimatePromoConfigurable() {
  override fun getId(): String = "promo.typescript"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.typescript")
  override fun createComponent(): JComponent = javascriptFeaturePage()
}

internal class PromoSwaggerConfigurable : UltimatePromoConfigurable() {
  override fun getId(): String = "promo.swagger"
  override fun getDisplayName(): String = FeaturePromoBundle.message("promo.configurable.swagger")

  override fun createComponent(): JComponent {
    return featurePage(
      FeaturePromoBundle.message("feature.swagger.description.html", "https://www.jetbrains.com/help/idea/openapi.html"),
      listOf(
        PromoFeatureListItem(AllIcons.Actions.ReformatCode, FeaturePromoBundle.message("feature.swagger.code")),
        PromoFeatureListItem(AllIcons.FileTypes.UiForm, FeaturePromoBundle.message("feature.swagger.preview")),
        PromoFeatureListItem(AllIcons.Actions.RunAll, FeaturePromoBundle.message("feature.swagger.httpclient"))
      ),
      "JavaScript"
    )
  }
}