// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

enum class PluginDistribution {
  ALL,
  NOT_FOR_RELEASE,
  NOT_FOR_PUBLIC_BUILDS,
  ;
}
