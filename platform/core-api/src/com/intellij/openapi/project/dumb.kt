// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.extensions.ExtensionPointName

fun <T : Any> ExtensionPointName<T>.lazyDumbAwareExtensions(project: Project): Sequence<T> {
  return if (DumbService.getInstance(project).isDumb) lazySequence().filter { DumbService.isDumbAware(it) } else lazySequence()
}