// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import org.jetbrains.annotations.ApiStatus

/**
 * Usage requires IJ Platform team approval (including plugin into white-list).
 */
@ApiStatus.Internal
interface ProjectServiceContainerCustomizer {
  companion object {
    @JvmStatic
    fun getEp(): ExtensionPointImpl<ProjectServiceContainerCustomizer> {
      return (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
        .getExtensionPoint("com.intellij.projectServiceContainerCustomizer")
    }
  }

  /**
   * Invoked after implementation classes for project's components were determined (and loaded),
   * but before components are instantiated.
   */
  fun serviceContainerInitialized(project: Project)
}