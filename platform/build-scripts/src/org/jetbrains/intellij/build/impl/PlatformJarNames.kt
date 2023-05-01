// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

/**
 * Names of JAR files from IDE_HOME/lib directory.
 */
object PlatformJarNames {
  /**
   * Used by default for modules and module-level libraries included in the platform part of the distribution.
   */
  internal const val APP_JAR: String = "app.jar"

  /**
   * Used by default for project-level libraries included in the platform part of the distribution.
   */
  internal const val LIB_JAR: String = "lib.jar"

  /**
   * Temporary created during the build for modules and libraries included in the platform part which need to be scrambled. 
   * Its content is merged in [APP_JAR] after scrambling.  
   */
  const val PRODUCT_JAR: String = "product.jar"
  
  /**
   * Used in some IDEs for modules containing classes which are used in tests only and therefore should not be included in the IDE's 
   * production classpath. 
   */
  internal const val TEST_FRAMEWORK_JAR: String = "testFramework.jar"
  
  /**
   * Used for `intellij.platform.runtime.repository` module which is added to the initial classpath when the new modular loader is used.  
   */
  internal const val RUNTIME_MODULE_REPOSITORY_JAR: String = "platform-runtime-repository.jar"
}