// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.api

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Sentence

interface RenameConflict {

  // TBD icon and severity

  @Nls(capitalization = Sentence)
  fun description(): String

  companion object {

    @JvmStatic
    fun fromText(@Nls(capitalization = Sentence) description: String): RenameConflict {
      return object : RenameConflict {
        override fun description(): String = description
      }
    }
  }
}
