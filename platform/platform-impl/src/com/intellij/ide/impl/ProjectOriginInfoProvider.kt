// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Provides information about project origin, e.g. the URL when the project has been cloned from.
 */
@ApiStatus.Internal
interface ProjectOriginInfoProvider {

  /**
   * Returns the URL which the project has been cloned from, or null if this information is unknown.
   */
  fun getOriginUrl(projectDir: Path): String?

}