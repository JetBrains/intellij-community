// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import gnu.trove.THashSet
import junit.framework.AssertionFailedError
import org.jetbrains.intellij.build.impl.LibraryLicensesListGenerator
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.junit.rules.ErrorCollector

class LibraryLicensesTester(private val project: JpsProject, private val licenses: List<LibraryLicense>) {
  private fun libraries(classpathKind: JpsJavaClasspathKind, moduleFilter: (JpsModule) -> Boolean = { true }): Map<String, JpsModule> {
    val nonPublicModules = setOf("intellij.idea.ultimate.build",
                                 "intellij.idea.community.build", "buildSrc",
                                 "intellij.platform.testGuiFramework")
    val libraries = HashMap<String, JpsModule>()
    project.modules.filter { it.name !in nonPublicModules
                             && !it.name.contains("guiTests")
                             && !it.name.contains("integrationTests", ignoreCase = true)
                             && moduleFilter(it)}.forEach { module ->
      JpsJavaExtensionService.dependencies(module).includedIn(classpathKind).libraries.forEach {
        val libraryName = LibraryLicensesListGenerator.getLibraryName(it)
        libraries[libraryName] = module
      }
    }
    return libraries
  }

  fun reportMissingLicenses(collector: ErrorCollector) {
    val libraries = libraries(JpsJavaClasspathKind.PRODUCTION_RUNTIME) { !it.name.startsWith("fleet") }
    val librariesWithLicenses = licenses.flatMapTo(THashSet()) { it.libraryNames }
    for ((libraryName, jpsModule) in libraries) {
      if (libraryName !in librariesWithLicenses) {
        collector.addError(AssertionFailedError("""
                  |License isn't specified for '$libraryName' library (used in module '${jpsModule.name}' in ${jpsModule.contentRootsList.urls})
                  |If a library is packaged into IDEA installation information about its license must be added into one of *LibraryLicenses.groovy files
                  |If a library is used in tests only change its scope to 'Test'
                  |If a library is used for compilation only change its scope to 'Provided'
        """.trimMargin()))
      }
    }
  }

  fun reportMissingLibrariesVersions(collector: ErrorCollector) {
    val libraries = libraries(JpsJavaClasspathKind.PRODUCTION_COMPILE) +
                    libraries(JpsJavaClasspathKind.PRODUCTION_RUNTIME) +
                    libraries(JpsJavaClasspathKind.TEST_COMPILE) +
                    libraries(JpsJavaClasspathKind.TEST_RUNTIME)
    licenses.filter {
      it.version.isNullOrBlank() &&
      it.license != LibraryLicense.JETBRAINS_OWN &&
      !libraries.containsKey(it.libraryName)
    }.forEach {
      collector.addError(
        AssertionFailedError(
          "Version of '${it.name ?: it.libraryName}' library must be specified in one of *LibraryLicenses.groovy files. " +
          "If it's built from custom revision please specify '${LibraryLicense.CUSTOM_REVISION}'"
        )
      )
    }
  }
}