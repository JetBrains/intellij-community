// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import org.assertj.core.api.SoftAssertions
import org.jetbrains.intellij.build.LibraryLicense
import org.jetbrains.intellij.build.impl.LibraryLicensesListGenerator
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule

class LibraryLicensesTester(private val project: JpsProject, private val licenses: List<LibraryLicense>) {
  fun reportMissingLicenses(collector: SoftAssertions) {
    val nonPublicModules = hashSetOf("intellij.workspaceModel.performanceTesting")
    val libraries = HashMap<JpsLibrary, JpsModule>()
    project.modules.filter {
      it.name !in nonPublicModules
      && !it.name.contains("guiTests")
      && it.name != "intellij.platform.util.immutableKeyValueStore.benchmark"
      && !it.name.contains("integrationTests", ignoreCase = true)
    }
      .forEach { module ->
        val enumerator = JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
        if (enumerator.modules
            .asSequence().plus(module)
            .any { it.name == "intellij.platform.buildScripts" }) {
          return@forEach
        }
        enumerator.libraries.forEach {
          libraries[it] = module
        }
      }

    val librariesWithLicenses = licenses.flatMap { it.getLibraryNames() }.toSet()

    for ((jpsLibrary, jpsModule) in libraries) {
      val libraryName = LibraryLicensesListGenerator.getLibraryName(jpsLibrary)
      if (libraryName !in librariesWithLicenses) {
        // require licence entry only for a main library (ktor-client), not for sub-libraries
        if (isImplicitLibrary(libraryName)) {
          continue
        }

        collector.collectAssertionError(AssertionError("""
                  |License isn't specified for '$libraryName' library (used in module '${jpsModule.name}' in ${jpsModule.contentRootsList.urls})
                  |If a library is packaged into IDEA installation information about its license must be added into one of *LibraryLicenses.kt files
                  |If a library is used in tests only change its scope to 'Test'
                  |If a library is used for compilation only change its scope to 'Provided'
        """.trimMargin()))
      }
    }
  }
}

private fun isImplicitLibrary(libraryName: String): Boolean {
  return ((libraryName.startsWith("ktor-") || libraryName.startsWith("io.ktor.")) && libraryName != "ktor-client")
         || libraryName.startsWith("skiko-awt-runtime-")
}
