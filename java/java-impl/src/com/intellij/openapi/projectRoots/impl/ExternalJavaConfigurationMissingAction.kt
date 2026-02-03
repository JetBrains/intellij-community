// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Extension point to provide actions when external Java configuration declares a JDK that is missing.
 */
public interface ExternalJavaConfigurationMissingAction {
  public companion object {
    public val EP_NAME: ExtensionPointName<ExternalJavaConfigurationMissingAction> =
      ExtensionPointName.create("com.intellij.openapi.projectRoots.externalJavaConfigurationMissingAction")
  }

  public fun <T> createAction(project: Project, provider: ExternalJavaConfigurationProvider<T>, releaseData: T): AnAction?
}
