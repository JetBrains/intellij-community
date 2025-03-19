// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

private class PluginUtilModernImpl : PluginUtilImpl() {
  private val walker = StackWalker.getInstance(setOf(StackWalker.Option.RETAIN_CLASS_REFERENCE), 5)

}