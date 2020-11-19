// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class DefaultSortWeight(val weight: Int) {
  PROJECT(-90),
  MODULE_GROUP(-80),
  MODULE_ROOT(-70),
  PACKAGE(-30),
  FOLDER(-20),
  FILE(20),
  SCRATCH_ROOT(9_000);
}
