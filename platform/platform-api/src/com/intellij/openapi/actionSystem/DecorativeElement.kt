// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

/**
 * [ActionGroup]'s can have decorative elements. These elements must not be performed.
 */
open class DecorativeElement : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    throw UnsupportedOperationException()
  }
}
