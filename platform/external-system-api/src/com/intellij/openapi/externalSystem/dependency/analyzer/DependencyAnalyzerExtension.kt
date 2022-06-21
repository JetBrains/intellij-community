// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointUtil
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

interface DependencyAnalyzerExtension {

  fun isApplicable(systemId: ProjectSystemId): Boolean

  fun createContributor(project: Project, parentDisposable: Disposable): DependencyAnalyzerContributor

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<DependencyAnalyzerExtension>("com.intellij.externalSystemDependencyAnalyzer")

    fun getExtension(systemId: ProjectSystemId): DependencyAnalyzerExtension =
      EP_NAME.findFirstSafe { it.isApplicable(systemId) }!!

    fun createExtensionDisposable(systemId: ProjectSystemId, parentDisposable: Disposable): Disposable {
      return ExtensionPointUtil.createExtensionDisposable(getExtension(systemId), EP_NAME)
        .also { parentDisposable.whenDisposed { Disposer.dispose(it) } }
    }
  }
}