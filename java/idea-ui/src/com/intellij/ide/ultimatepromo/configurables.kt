// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ultimatepromo

import com.intellij.icons.AllIcons
import com.intellij.java.ui.icons.JavaUIIcons
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.*
import javax.swing.Icon
import javax.swing.JComponent

internal class PromoDatabaseConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable = PromoDatabaseConfigurable()
}

internal class PromoDatabaseConfigurable : ConfigurableWithId, Configurable.Promo {
  override fun getId(): String = "promo.database"

  override fun getDisplayName(): String {
    return FeaturePromoBundle.message("promo.configurable.database")
  }

  override fun createComponent(): JComponent {
    return PromoPages.build(
      PromoFeaturePage(
        JavaUIIcons.IdeaUltimatePromo,
        PluginAdvertiserService.ideaUltimate,
        FeaturePromoBundle.message("feature.database.description.html"),
        listOf(
          PromoFeatureListItem(
            AllIcons.Nodes.DataTables,
            FeaturePromoBundle.message("feature.database.create.and.manage")
          ),
          PromoFeatureListItem(
            AllIcons.Actions.Run_anything,
            FeaturePromoBundle.message("feature.database.run")
          ),
          PromoFeatureListItem(
            AllIcons.ToolbarDecorator.Import,
            FeaturePromoBundle.message("feature.database.export")
          )
        ),
        FeaturePromoBundle.message("free.trial.hint"),
        "com.intellij.database"
      )
    )
  }

  override fun isModified(): Boolean = false

  override fun apply() {
  }

  override fun getPromoIcon(): Icon = AllIcons.Ultimate.Lock
}