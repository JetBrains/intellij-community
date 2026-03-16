// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.customization.console

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.search.GlobalSearchScope

const val SLF4J_MAVEN: String = "org.slf4j:slf4j-api"
private const val LOG4J_MAVEN = "org.apache.logging.log4j:log4j-api"
private const val COMMON_LOGGING_MAVEN = "commons-logging:commons-logging"
private val LOG_MAVEN = setOf(SLF4J_MAVEN, LOG4J_MAVEN, COMMON_LOGGING_MAVEN)

class ClassLoggingConsoleFilterProvider : ConsoleFilterProvider {
  override fun getDefaultFilters(project: Project): Array<Filter> {
    if (!AdvancedSettings.getBoolean("process.console.output.to.find.class.names")) {
      return Filter.EMPTY_ARRAY
    }

    val hasLoggingSystems = ApplicationManager.getApplication().runReadAction(Computable {
      JavaLibraryUtil.hasAnyLibraryJar(project, LOG_MAVEN)
    })

    if (!hasLoggingSystems) {
      return Filter.EMPTY_ARRAY
    }

    return arrayOf(ClassFinderFilter(project, GlobalSearchScope.allScope(project)))
  }
}
