// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.customization.console

import com.intellij.codeInspection.logging.LoggingUtil
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

class ClassLoggingConsoleFilterProvider : ConsoleFilterProvider {
  override fun getDefaultFilters(project: Project): Array<Filter> {
    if (!AdvancedSettings.getBoolean("process.console.output.to.find.class.names")) {
      return Filter.EMPTY_ARRAY
    }
    if (DumbService.isDumb(project)) {
      return Filter.EMPTY_ARRAY
    }
    val modules = ModuleManager.getInstance(project).modules
    if (modules.none { ModuleTypeId.JAVA_MODULE.equals(ModuleType.get(it).id, ignoreCase = true) }) {
      return Filter.EMPTY_ARRAY
    }

    if (!(JavaLibraryUtil.hasLibraryClass(project, LoggingUtil.SLF4J_LOGGER) ||
          JavaLibraryUtil.hasLibraryClass(project, LoggingUtil.LOG4J_LOGGER) ||
          JavaPsiFacade.getInstance(project).findClass(LoggingUtil.IDEA_LOGGER, GlobalSearchScope.allScope(project)) != null)) {
      return Filter.EMPTY_ARRAY
    }
    return arrayOf(ClassFinderFilter(project, GlobalSearchScope.allScope(project)))
  }
}
