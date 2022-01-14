// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.annotations.Nls

interface DependencyAnalyzerProject: UserDataHolder {

  val path: String

  val title: @Nls String
}