// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleRepository

import org.jetbrains.jps.model.module.JpsModule

/**
 * Provides a way to detect if a [JpsModule] is registered as a content module and obtain the registration data.
 * This is needed to generate proper information about the module in the runtime module repository.
 */
interface ContentModuleDetector {
  fun findContentModuleData(jpsModule: JpsModule): ContentModuleRegistrationDataForHeader?

  /**
   * A separate method is needed because content module descriptors for test sources use a special `._test` suffix.
   * This method won't be needed after IJPL-242652 is implemented.
   */
  fun findContentModuleDataForTests(jpsModule: JpsModule): ContentModuleRegistrationDataForHeader?
}

