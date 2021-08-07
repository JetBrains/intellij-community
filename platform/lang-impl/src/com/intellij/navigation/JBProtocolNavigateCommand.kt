// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation

import com.intellij.openapi.diagnostic.logger

private val LOG = logger<JBProtocolNavigateCommand>()

open class JBProtocolNavigateCommand: JBProtocolNavigateCommandBase(NAVIGATE_COMMAND) {
  companion object {
    const val NAVIGATE_COMMAND = "navigate"
  }

  override fun perform(target: String, parameters: Map<String, String>) {
    if (target != REFERENCE_TARGET) {
      LOG.warn("JB navigate action supports only reference target, got $target")
      return
    }
    openProject(parameters) {
      findAndNavigateToReference(it, parameters)
    }
  }
}
