// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus

/**
 * Specifies which modules and libraries from the platform part of the distribution are used from the frontend process (JetBrains Client) and therefore need to be
 * put to separate JAR files to avoid loading unnecessary components when the frontend is started from the IDE. 
 * The instance of this class is obtained from [BuildContext.getFrontendModuleFilter] where it's automatically computed based on the value of 
 * [ProductProperties.embeddedFrontendRootModule].
 */
@ApiStatus.Experimental
interface FrontendModuleFilter {
  fun isModuleIncluded(moduleName: String): Boolean
  fun isProjectLibraryIncluded(libraryName: String): Boolean
}
