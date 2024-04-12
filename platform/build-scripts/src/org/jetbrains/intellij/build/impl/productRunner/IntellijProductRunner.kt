// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productRunner

import org.jetbrains.intellij.build.BuildContext

/**
 * Provides a way to run an IDE which distribution is currently being built by the build scripts.
 * This can be used to obtain some resources and include them in the distribution.
 */
internal interface IntellijProductRunner {
  suspend fun runProduct(arguments: List<String>, additionalSystemProperties: Map<String, String> = emptyMap(),
                         isLongRunning: Boolean = false)
  
  companion object {
    suspend fun createRunner(context: BuildContext): IntellijProductRunner {
      if (context.useModularLoader) {
        return ModuleBasedProductRunner(context.productProperties.rootModuleForModularLoader!!, context)
      }
      return createDevIdeBuild(context) 
    }
  }
}