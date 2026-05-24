// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.discovery

object ModuleSetSourceLabels {
  const val COMMUNITY: String = "community"
  const val CORE: String = "core"
  const val ULTIMATE: String = "ultimate"

  @JvmField
  val COMMUNITY_LABELS: Set<String> = setOf(COMMUNITY)
}
