// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import org.assertj.core.api.SoftAssertions
import org.jetbrains.intellij.build.LibraryLicense
import org.jetbrains.intellij.build.impl.getLibraryFilename
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule

fun reportMissingLicenses(collector: SoftAssertions, project: JpsProject, licenses: List<LibraryLicense>) {
  val nonPublicModules = hashSetOf("intellij.workspaceModel.performanceTesting")
  val libraries = HashMap<JpsLibrary, JpsModule>()
  project.modules
    .asSequence()
    .filter {
      !nonPublicModules.contains(it.name)
      && !it.name.contains("guiTests")
      && it.name != "intellij.platform.util.immutableKeyValueStore.benchmark"
      && it.name != "intellij.libraries.mockito"
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

  val librariesWithLicenses = licenses.flatMapTo(HashSet()) { it.getLibraryNames() }

  for ((jpsLibrary, jpsModule) in libraries) {
    val libraryName = getLibraryFilename(jpsLibrary)
    if (librariesWithLicenses.contains(libraryName)) {
      continue
    }

    // require license entry only for a main library (ktor-client), not for sub-libraries
    // we have `Ant` (uppercase `A`) as dir with JARs, and from maven `ant` (lowercase `a`) - license is specified for "Ant" (later we will remove `Ant`)
    if (isImplicitLibrary(libraryName) || libraryName == "ant") {
      continue
    }

    collector.collectAssertionError(
      AssertionError(
        """
              |License isn't specified for '$libraryName' library (used in module '${jpsModule.name}' in ${jpsModule.contentRootsList.urls})
              |If a library is packaged into IDEA installation information about its license must be added into one of *LibraryLicenses.kt files
              |If a library is used in tests only change its scope to 'Test'
              |If a library is used for compilation only change its scope to 'Provided'
    """.trimMargin()
      )
    )
  }
}

private fun isImplicitLibrary(libraryName: String): Boolean {
  return ((libraryName.startsWith("ktor-") || libraryName.startsWith("io.ktor.")) && (libraryName != "ktor-client")) ||
         libraryName.startsWith("skiko-awt-runtime-")
}
