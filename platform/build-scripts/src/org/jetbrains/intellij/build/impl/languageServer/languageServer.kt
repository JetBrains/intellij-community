// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.languageServer

import com.intellij.platform.buildData.productInfo.CustomCommandLaunchData
import org.jetbrains.intellij.build.BuildContext

internal fun generateLspServerLaunchData(ideContext: BuildContext): CustomCommandLaunchData {
  return CustomCommandLaunchData(
    commands = listOf("run"),
    bootClassPathJarNames = ideContext.bootClassPathJarNames,
    mainClass = "com.jetbrains.ls.kotlinLsp.KotlinLspServerKt",
  )
}