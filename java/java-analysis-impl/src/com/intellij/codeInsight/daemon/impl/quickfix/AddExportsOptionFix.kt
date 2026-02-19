// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.java.codeserver.core.JpmsModuleAccessInfo
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.Nls

public class AddExportsOptionFix(module: Module,
                                  targetName: String,
                                  packageName: String,
                                  private val useName: String) : CompilerOptionFix(module) {
  private val qualifier = "${targetName}/${packageName}"

  override fun getText(): @Nls String = QuickFixBundle.message("add.compiler.option.fix.name", "${JpmsModuleAccessInfo.ADD_EXPORTS_OPTION} ${qualifier}=${useName}")

  override fun update(options: MutableList<String>) {
    var idx = -1
    var candidate = -1
    var offset = 0
    for ((i, option) in options.withIndex()) {
      if (option.startsWith(JpmsModuleAccessInfo.ADD_EXPORTS_OPTION)) {
        if (option.length == JpmsModuleAccessInfo.ADD_EXPORTS_OPTION.length) {
          candidate = i + 1; offset = 0
        }
        else if (option[JpmsModuleAccessInfo.ADD_EXPORTS_OPTION.length] == '=') {
          candidate = i; offset = JpmsModuleAccessInfo.ADD_EXPORTS_OPTION.length + 1
        }
      }
      if (i == candidate && option.startsWith(qualifier, offset)) {
        val qualifierEnd = qualifier.length + offset
        if (option.length == qualifierEnd || option[qualifierEnd] == '=') {
          idx = i
        }
      }
    }
    when (idx) {
      -1 -> options += listOf(JpmsModuleAccessInfo.ADD_EXPORTS_OPTION, "${qualifier}=${useName}")
      else -> options[idx] = "${options[idx].trimEnd(',')},${useName}"
    }
  }
}