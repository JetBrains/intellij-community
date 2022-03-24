// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.util.ui.EmptyIcon
import java.util.concurrent.ConcurrentSkipListSet
import javax.swing.Icon

/**
 * Provides external system specific icons for actions and other common external system UI elements that should be visually identified.
 */
interface ExternalSystemIconProvider {

  /**
   * Icon for auto-reload action in editor floating toolbar (ExternalSystem.ProjectRefreshAction).
   */
  @JvmDefault
  val reloadIcon: Icon
    get() = AllIcons.Actions.BuildLoadChanges

  /**
   * Icon for project selector in dependency analyzer.
   * @see com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerExtension
   */
  @JvmDefault
  val projectIcon: Icon
    get() = EmptyIcon.ICON_16

  companion object {

    private val EP_COLLECTOR = KeyedExtensionCollector<ExternalSystemIconProvider, ProjectSystemId>("com.intellij.externalIconProvider")
    private val LOG = Logger.getInstance(ExternalSystemIconProvider::class.java)

    @JvmStatic
    fun getExtension(systemId: ProjectSystemId): ExternalSystemIconProvider {
      val iconProvider = EP_COLLECTOR.findSingle(systemId)
      if (iconProvider != null) return iconProvider
      warnOnce("Cannot find ExternalSystemIconProvider for $systemId. Fallback to default provider")
      return object : ExternalSystemIconProvider {}
    }

    private val messages = ConcurrentSkipListSet<String>()
    private fun warnOnce(message: String) {
      if (messages.add(message)) {
        LOG.warn(message, Throwable())
      }
    }
  }
}