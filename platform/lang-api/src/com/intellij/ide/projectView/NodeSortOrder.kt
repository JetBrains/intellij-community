// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class NodeSortOrder {
  PROJECT_ROOT,
  MODULE_GROUP,
  MODULE_ROOT,
  PACKAGE,
  FOLDER,
  MANUAL,
  UNSPECIFIED,
  LIBRARY_ROOT,
  SCRATCH_ROOT,
}
