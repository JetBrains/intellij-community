// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

/**
 * Provides external system specific icons for actions and other common external system UI elements that should be visually identified.
 */
interface ExternalSystemIconProvider {

  /**
   * Icon for auto-reload action in editor floating toolbar (ExternalSystem.ProjectRefreshAction).
   */
  val reloadIcon: Icon
    get() = AllIcons.Actions.BuildLoadChanges

  /**
   * Icon for project selector in dependency analyzer.
   * @see com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerExtension
   */
  val projectIcon: Icon
    get() = EmptyIcon.ICON_16

  companion object {

    private val EP_COLLECTOR = KeyedExtensionCollector<ExternalSystemIconProvider, ProjectSystemId>("com.intellij.externalIconProvider")

    @JvmStatic
    fun getExtension(systemId: ProjectSystemId): ExternalSystemIconProvider {
      val iconProvider = EP_COLLECTOR.findSingle(systemId)
      if (iconProvider != null) return iconProvider
      logger<ExternalSystemIconProvider>().debug("Cannot find ExternalSystemIconProvider for $systemId. Fallback to default provider")
      return object : ExternalSystemIconProvider {}
    }
  }
}