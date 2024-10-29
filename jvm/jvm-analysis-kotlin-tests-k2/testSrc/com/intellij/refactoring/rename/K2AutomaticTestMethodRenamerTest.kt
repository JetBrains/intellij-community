// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2AutomaticTestMethodRenamerTest : KotlinAutomaticTestMethodRenamerTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}