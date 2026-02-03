// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.tests.kotlin

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2RedundantRequiresStatementTest : KotlinRedundantRequiresStatementTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}