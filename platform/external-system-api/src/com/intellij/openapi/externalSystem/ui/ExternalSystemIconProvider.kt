// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.ui

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.util.KeyedExtensionCollector
import javax.swing.Icon

interface ExternalSystemIconProvider {

  val reloadIcon: Icon

  companion object {

    private val EP_COLLECTOR = KeyedExtensionCollector<ExternalSystemIconProvider, ProjectSystemId>("com.intellij.externalIconProvider")

    @JvmStatic
    fun getExtension(systemId: ProjectSystemId): ExternalSystemIconProvider {
      return EP_COLLECTOR.findSingle(systemId) ?: DefaultExternalSystemIconProvider
    }
  }
}