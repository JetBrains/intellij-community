// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import java.util.function.Consumer

/**
 * @author Konstantin Bulenkov
 */
interface SearchTopHitProvider {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<SearchTopHitProvider> = ExtensionPointName("com.intellij.search.topHitProvider")

    @JvmStatic
    fun getTopHitAccelerator(): @NlsSafe String = "/"
  }

  fun consumeTopHits(pattern: String, collector: Consumer<Any>, project: Project?)
}
