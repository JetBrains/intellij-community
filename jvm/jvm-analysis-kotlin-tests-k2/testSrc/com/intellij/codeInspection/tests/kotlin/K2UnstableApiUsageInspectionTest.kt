// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.kotlin

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2UnstableApiUsageInspectionTest : KotlinUnstableApiUsageInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}