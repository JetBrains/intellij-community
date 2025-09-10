// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.FrontendModuleFilter

/**
 * Names of JAR files from `IDE_HOME/lib` directory.
 * These names are implementation detail and may be changed in the future; code outside the build scripts must not rely on them.
 */
object PlatformJarNames {
  /**
   * Used by default for modules and module-level libraries included in the platform part of the distribution.
   */
  internal const val APP_BACKEND_JAR: String = "app-backend.jar"

  /**
   * Used by default for modules and module-level libraries included in the platform part of the distribution, which are also used by 
   * JetBrains Client.
   */
  internal const val APP_JAR: String = "app.jar"

  /**
   * Returns the name of the default JAR for a platform module.
   */
  internal fun getPlatformModuleJarName(moduleName: String, frontendModuleFilter: FrontendModuleFilter): String {
    if (frontendModuleFilter.isBackendModule(moduleName)) {
      if (moduleName.startsWith("fleet.")) {
        return LIB_BACKEND_JAR
      }
      return APP_BACKEND_JAR
    }
    else {
      if (moduleName.startsWith("fleet.")) {
        return LIB_JAR
      }
      return APP_JAR
    }
  }

  /**
   * Used by default for project-level libraries included in the platform part of the distribution.
   */
  internal const val LIB_BACKEND_JAR: String = "lib-backend.jar"

  /**
   * Used by default for project-level libraries included in the platform part of the distribution, which are also used by JetBrains Client. 
   */
  internal const val LIB_JAR: String = "lib.jar"

  /**
   * Used for modules and libraries included in the platform part which need to be scrambled. 
   */
  const val PRODUCT_BACKEND_JAR: String = "product-backend.jar"

  /**
   * Used for modules and libraries which need to be scrambled and used by JetBrains Client.  
   */
  const val PRODUCT_JAR: String = "product.jar"
  
  /**
   * Used in some IDEs for modules containing classes which are used in tests only and therefore should not be included in the IDE's 
   * production classpath. 
   */
  const val TEST_FRAMEWORK_JAR: String = "testFramework.jar"

  /**
   * The custom filesystem implementations for Eel and Fleet/FSD.
   */
  const val PLATFORM_CORE_NIO_FS: String = "nio-fs.jar"
}
