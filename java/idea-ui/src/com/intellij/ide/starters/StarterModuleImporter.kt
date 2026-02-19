// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters

import com.intellij.openapi.module.Module

interface StarterModuleImporter {
  val id: String
  val title: String

  /**
   * Runs import of the module from build system scripts.
   *
   * @param module Model of created project
   * @return false to stop processing
   */
  fun runAfterSetup(module: Module): Boolean
}