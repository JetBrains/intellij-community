// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

package com.intellij.openapi.application

open class ModernApplicationStarter {
  open val commandName: String = ""
  open fun premain(args: List<String>) {}
  open suspend fun start(args: List<String>) {}
}