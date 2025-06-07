// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus

/**
 * Specifies which modules and libraries from the platform part of the distribution can be used from the frontend process (JetBrains Client) and therefore need to be
 * put to separate JAR files to avoid loading unnecessary components when the frontend is started from the IDE. 
 * The instance of this class is obtained from [BuildContext.getFrontendModuleFilter] where it's automatically computed based on the value of 
 * [ProductProperties.embeddedFrontendRootModule].
 */
@ApiStatus.Experimental
interface FrontendModuleFilter {
  /**
   * Returns `true` if module [moduleName] is included in the frontend variant of the distribution.
   */
  fun isModuleIncluded(moduleName: String): Boolean

  /**
   * Returns `true` if a project library [libraryName] is included in the frontend variant of the distribution.
   */
  fun isProjectLibraryIncluded(libraryName: String): Boolean

  /**
   * Returns `true` if module [moduleName] can be loaded by the frontend process (JetBrains Client) according to its dependencies.
   * Unlike [isModuleIncluded] this doesn't indicate that the module will be included in the frontend variant of this particular IDE.
   * This function is supposed to be used for plugins, including non-bundled ones, where it's not known in which specific frontend variant a plugin will be included.
   */
  fun isModuleCompatibleWithFrontend(moduleName: String): Boolean
}
