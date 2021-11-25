// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard

import kotlin.math.abs
import kotlin.random.Random

class Session private constructor(val id: Int) {
  companion object {
    @JvmStatic
    fun createRandomId(): Session =
      Session(id = abs(Random.nextInt()))
  }
}