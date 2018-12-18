/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.testGuiFramework.tests.community.welcomeFrame

import java.io.Serializable

data class TestCounterParameters(val counter: Int) : Serializable {
  override fun toString() = counter.toString()
}