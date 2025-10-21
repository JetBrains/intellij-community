// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.logs

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.mlapi.feature.ClassFeatureDeclaration
import com.jetbrains.mlapi.feature.EnumFeatureDeclaration
import com.jetbrains.mlapi.feature.FeatureDeclaration
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IJFeatureDeclarations {
  inline fun <reified E: Enum<*>> enum(name: String, noinline lazyDescription: (() -> String)? = null): EnumFeatureDeclaration<E> =
    FeatureDeclaration.enum<E>(name, null, lazyDescription)

  fun aClass(name: String, lazyDescription: (() -> String)? = null): ClassFeatureDeclaration =
    FeatureDeclaration.aClass(name, null, lazyDescription, classCheckAndTransform)

  private val classCheckAndTransform: (Class<*>) -> String = {
    if (getPluginInfo(it).isSafeToReport()) StringUtil.substringBeforeLast(it.name, "$\$Lambda", true) else "third.party"
  }
}