// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.java.codeserver.core.JpmsModuleAccessInfo
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.Nls

public class AddModulesOptionFix(module: Module, private val moduleName: String) : CompilerOptionFix(module) {
  override fun getText(): @Nls String = QuickFixBundle.message("add.compiler.option.fix.name", "${JpmsModuleAccessInfo.ADD_MODULES_OPTION} ${moduleName}")

  override fun update(options: MutableList<String>) {
    var idx = -1
    for ((i, option) in options.withIndex()) {
      if (option.startsWith(JpmsModuleAccessInfo.ADD_MODULES_OPTION)) {
        if (option.length == JpmsModuleAccessInfo.ADD_MODULES_OPTION.length) idx = i + 1
        else if (option[JpmsModuleAccessInfo.ADD_MODULES_OPTION.length] == '=') idx = i
      }
    }
    when (idx) {
      -1 -> options += listOf(JpmsModuleAccessInfo.ADD_MODULES_OPTION, moduleName)
      options.size -> options += moduleName
      else -> {
        val value = options[idx]
        options[idx] = if (value.endsWith('=') || value.endsWith(',')) value + moduleName else "${value},${moduleName}"
      }
    }
  }

}