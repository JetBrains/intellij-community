// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

internal class TargetEnvironmentDetailsConfigurable(
  private val project: Project,
  private val config: TargetEnvironmentConfiguration,
  defaultLanguage: LanguageRuntimeType<*>?,
  treeUpdate: Runnable
) : NamedConfigurable<TargetEnvironmentConfiguration>(true, treeUpdate) {

  private val targetConfigurable: Configurable = config.getTargetType()
    .createConfigurable(project, config, defaultLanguage, this)

  private var languagesPanel: TargetEnvironmentLanguagesPanel? = null

  override fun getBannerSlogan(): String = config.displayName

  override fun getIcon(expanded: Boolean): Icon = config.getTargetType().icon

  override fun isModified(): Boolean =
    targetConfigurable.isModified || languagesPanel?.isModified == true

  override fun getDisplayName(): String = config.displayName

  override fun apply() {
    targetConfigurable.apply()
    languagesPanel?.applyAll()
  }

  override fun reset() {
    targetConfigurable.reset()
    languagesPanel?.reset()
  }

  override fun setDisplayName(name: String) {
    config.displayName = name
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    targetConfigurable.disposeUIResources()
    languagesPanel?.disposeUIResources()
  }

  override fun getEditableObject() = config

  override fun createOptionsPanel(): JComponent {
    val panel = JPanel(VerticalLayout(UIUtil.DEFAULT_VGAP))
    panel.border = JBUI.Borders.empty(0, 10, 10, 10)

    panel.add(targetConfigurable.createComponent() ?: throw IllegalStateException())

    val targetSupplier: Supplier<TargetEnvironmentConfiguration>
    if (targetConfigurable is BrowsableTargetEnvironmentType.ConfigurableCurrentConfigurationProvider)
      targetSupplier = Supplier<TargetEnvironmentConfiguration>(targetConfigurable::getCurrentConfiguration)
    else {
      targetSupplier = Supplier<TargetEnvironmentConfiguration> { config }
    }

    languagesPanel = TargetEnvironmentLanguagesPanel(project, config.getTargetType(), targetSupplier, config.runtimes) {
      forceRefreshUI()
    }
    panel.add(languagesPanel!!.component)

    return JBScrollPane(panel).also {
      it.border = JBUI.Borders.empty()
    }
  }

  override fun resetOptionsPanel() {
    languagesPanel?.disposeUIResources()
    languagesPanel = null
    super.resetOptionsPanel()
  }

  private fun forceRefreshUI() {
    createComponent()?.let {
      it.revalidate()
      it.repaint()
    }
  }
}