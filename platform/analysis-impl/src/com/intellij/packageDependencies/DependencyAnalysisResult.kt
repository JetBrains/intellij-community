// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
open class DependencyAnalysisResult(
  val builders: MutableList<DependenciesBuilder> = arrayListOf()
) {

  @get:NlsContexts.TabTitle
  lateinit var panelDisplayName: String

  fun addBuilder(builder: DependenciesBuilder) {
    builders += builder;
  }

}
