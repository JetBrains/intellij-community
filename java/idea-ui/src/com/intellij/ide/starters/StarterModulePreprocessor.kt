// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.Module

interface StarterModulePreprocessor {
  fun process(module: Module, moduleBuilder: ModuleBuilder, frameworkVersion: String?)
}