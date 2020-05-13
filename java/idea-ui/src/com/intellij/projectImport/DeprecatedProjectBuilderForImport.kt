// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectImport

import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface of deprecated project builder.
 * Import action with {@link ProjectBuilder} marked by this
 * interface will be replaced on open action if possible.
 *
 * Needed to remove the redundant import action.
 */
@ApiStatus.Experimental
interface DeprecatedProjectBuilderForImport {
  fun getProjectOpenProcessor(): ProjectOpenProcessor
}
