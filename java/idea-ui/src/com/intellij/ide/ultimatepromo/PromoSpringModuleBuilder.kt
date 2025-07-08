// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ultimatepromo

import com.intellij.icons.AllIcons
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.util.projectWizard.*
import com.intellij.ide.wizard.withVisualPadding
import com.intellij.java.ui.icons.JavaUIIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import javax.swing.Icon
import javax.swing.JComponent

@NlsSafe
private const val SPRING_NAME = "Spring"
private const val SPRING_PLUGIN_ID = "com.intellij.spring"

internal class PromoSpringModuleBuilder : ModuleBuilder(), PromoModuleBuilder {
  override fun isAvailable(): Boolean = Registry.`is`("idea.ultimate.features.hints.enabled")

  override fun getModuleType(): ModuleType<*> = JavaModuleType.getModuleType()
  override fun getWeight(): Int = JVM_WEIGHT

  override fun getBuilderId(): String = "promo-spring"
  override fun getNodeIcon(): Icon = JavaUIIcons.SpringPromo
  override fun getPresentableName(): String = SPRING_NAME
  override fun getDescription(): String = JavaUiBundle.message("feature.spring.wizard.description")

  override fun modifyProjectTypeStep(settingsStep: SettingsStep): ModuleWizardStep? = null

  override fun getCustomOptionsStep(context: WizardContext?, parentDisposable: Disposable?): ModuleWizardStep {
    return object : ModuleWizardStep() {
      val page = PromoFeaturePage(
        JavaUIIcons.IdeaUltimatePromo,
        PluginAdvertiserService.ideaUltimate,
        JavaUiBundle.message("feature.spring.description.html"),
        listOf(
          PromoFeatureListItem(
            AllIcons.RunConfigurations.Application,
            JavaUiBundle.message("feature.spring.run.config")
          ),
          PromoFeatureListItem(
            AllIcons.FileTypes.Properties,
            JavaUiBundle.message("feature.spring.config.files")
          ),
          PromoFeatureListItem(
            AllIcons.Nodes.DataTables,
            JavaUiBundle.message("feature.spring.data")
          ),
          PromoFeatureListItem(
            AllIcons.FileTypes.Diagram,
            JavaUiBundle.message("feature.spring.navigation")
          )
        ),
        FeaturePromoBundle.message("free.trial.hint"),
        SPRING_PLUGIN_ID
      )
      private val panel: JComponent = PromoPages.buildWithTryUltimate(page, source = FUSEventSource.NEW_PROJECT_WIZARD).withVisualPadding()

      override fun updateDataModel(): Unit = Unit
      override fun getComponent(): JComponent = panel

      override fun validate(): Boolean {
        FUSEventSource.NEW_PROJECT_WIZARD.openDownloadPageAndLog(null, PluginAdvertiserService.ideaUltimate.downloadUrl,
                                                                 PluginAdvertiserService.ideaUltimate,
                                                                 PluginId.getId(SPRING_PLUGIN_ID))

        return false
      }

      override fun updateStep() {
        FUSEventSource.NEW_PROJECT_WIZARD.logIdeSuggested(null, PluginAdvertiserService.ideaUltimate.productCode,
                                                          PluginId.getId(SPRING_PLUGIN_ID))
      }
    }
  }
}