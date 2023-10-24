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
    if (!Registry.`is`("idea.ultimate.features.hints.enabled")) return null

    return clazz.java.getConstructor().newInstance()
  }
}

private fun featurePage(@NlsContexts.Label title: String, items: List<PromoFeatureListItem>, pluginId: String): JComponent {
  return PromoPages.build(PromoFeaturePage(
    JavaUIIcons.IdeaUltimatePromo,
    PluginAdvertiserService.ideaUltimate,
    title,
    items,
    FeaturePromoBundle.message("free.trial.hint"),
    pluginId
  ))
}

internal class PromoDatabaseConfigurableProvider : UltimateConfigurableProvider(PromoDatabaseConfigurable::class)
internal class PromoKubernetesConfigurableProvider : UltimateConfigurableProvider(PromoKubernetesConfigurable::class)
internal class PromoProfilerConfigurableProvider : UltimateConfigurableProvider(PromoProfilerConfigurable::class)

internal class PromoDatabaseConfigurable : UltimatePromoConfigurable() {
  override fun getId(): String = "promo.database"

  override fun getDisplayName(): String {
    return FeaturePromoBundle.message("promo.configurable.database")
  }

  override fun createComponent(): JComponent {
    return featurePage(
      FeaturePromoBundle.message("feature.database.description.html"),
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

  override fun getDisplayName(): String {
    return FeaturePromoBundle.message("promo.configurable.kubernetes")
  }

  override fun createComponent(): JComponent {
    return featurePage(
      FeaturePromoBundle.message("feature.kubernetes.description.html"),
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

  override fun getDisplayName(): String {
    return JavaUiBundle.message("promo.configurable.profiler")
  }

  override fun createComponent(): JComponent {
    return featurePage(
      JavaUiBundle.message("feature.profiler.description.html"),
      listOf(
        PromoFeatureListItem(AllIcons.Actions.ProfileCPU, JavaUiBundle.message("feature.profiler.cpu")),
        PromoFeatureListItem(AllIcons.Actions.ProfileMemory, JavaUiBundle.message("feature.profiler.memory")),
        PromoFeatureListItem(AllIcons.Actions.ProfileRed, JavaUiBundle.message("feature.profiler.hints"))
      ),
      "com.intellij.LineProfiler"
    )
  }
}