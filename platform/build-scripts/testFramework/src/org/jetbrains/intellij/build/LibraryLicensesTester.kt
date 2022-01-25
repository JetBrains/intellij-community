// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import gnu.trove.THashSet
import junit.framework.AssertionFailedError
import org.jetbrains.intellij.build.impl.LibraryLicensesListGenerator
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule
import org.junit.rules.ErrorCollector

class LibraryLicensesTester(private val project: JpsProject, private val licenses: List<LibraryLicense>) {
  fun reportMissingLicenses(collector: ErrorCollector) {
    val nonPublicModules = setOf("intellij.idea.ultimate.build",
                                 "intellij.idea.community.build",
                                 "buildSrc",
                                 "intellij.workspaceModel.performanceTesting")
    val libraries = HashMap<JpsLibrary, JpsModule>()
    project.modules.filter { it.name !in nonPublicModules
                             && !it.name.contains("guiTests")
                             && !it.name.startsWith("fleet.draft")
                             && it.name != "intellij.platform.util.immutableKeyValueStore.benchmark"
                             && !it.name.contains("integrationTests", ignoreCase = true)}.forEach { module ->
      JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries.forEach {
        libraries[it] = module
      }
    }

    val librariesWithLicenses = licenses.flatMapTo(THashSet()) { it.libraryNames }

    for ((jpsLibrary, jpsModule) in libraries) {
      val libraryName = LibraryLicensesListGenerator.getLibraryName(jpsLibrary)
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
}