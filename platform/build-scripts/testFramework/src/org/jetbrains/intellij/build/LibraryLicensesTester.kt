// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import gnu.trove.THashSet
import junit.framework.AssertionFailedError
import org.jetbrains.intellij.build.impl.LibraryLicensesListGenerator
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.junit.rules.ErrorCollector

class LibraryLicensesTester(private val project: JpsProject, private val licenses: List<LibraryLicense>) {
  fun reportMissingLicenses(collector: ErrorCollector) {
    val nonPublicModules = setOf("intellij.idea.ultimate.build", "intellij.idea.community.build", "buildSrc", "intellij.platform.testGuiFramework")
    val libraries = HashMap<JpsLibrary, JpsModule>()
    project.modules.filter { it.name !in nonPublicModules && !it.name.contains("guiTests") }.forEach { module ->
      JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries.forEach {
        libraries[it] = module
      }
    }

    val librariesWithLicenses = licenses.flatMapTo(THashSet()) { it.libraryNames }
    val unusedLibrariesWithLicenses = licenses.filter { it.libraryName != null }.mapNotNullTo(THashSet()) { it.libraryName }

    // what for?
    unusedLibrariesWithLicenses.remove("Servlets")

    for ((jpsLibrary, jpsModule) in libraries) {
      for (libName in LibraryLicensesListGenerator.getLibraryNames(jpsLibrary)) {
        if (libName !in librariesWithLicenses) {
          collector.addError(AssertionFailedError("""
                  |License isn't specified for '$libName' library (used in module '${jpsModule.name}' in ${jpsModule.contentRootsList.urls})
                  |If a library is packaged into IDEA installation information about its license must be added into one of *LibraryLicenses.groovy files
                  |If a library is used in tests only change its scope to 'Test'
                  |If a library is used for compilation only change its scope to 'Provided'
        """.trimMargin()))
        }
        unusedLibrariesWithLicenses.remove(libName)
        // transitive deps
        val files = jpsLibrary.getFiles(JpsOrderRootType.COMPILED)
        for (file in files) {
          val nameWithoutExtension = file.nameWithoutExtension
          unusedLibrariesWithLicenses.remove(nameWithoutExtension.substringBefore('-'))
          unusedLibrariesWithLicenses.remove(nameWithoutExtension)
          unusedLibrariesWithLicenses.remove(file.name)
        }
      }
    }

    if (unusedLibrariesWithLicenses.isNotEmpty()) {
      collector.addError(AssertionFailedError("License specified for unknown libraries:\n\n${unusedLibrariesWithLicenses.joinToString("\n")}"))
    }
  }
}