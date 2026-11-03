// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

/**
 * The values of the option `intellij.build.test.search.scope`.
 */
enum class TestSearchScope(val serialized: String) {
  SINGLE_MODULE("singleModule"),
  MODULE_WITH_DEPENDENCIES("moduleWithDependencies"),
}
