// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo

internal fun generateDeps(
  target: Target,
  module: ModuleDescriptor,
  resourceDependencies: List<String>,
  hasSources: Boolean,
  isTest: Boolean = false,
  context: BazelBuildFileGenerator,
) {
  val deps = ArrayList<String>()
  val exports = mutableListOf<String>()
  val runtimeDeps = mutableListOf<String>()

  resourceDependencies.mapTo(runtimeDeps) { ":$it" }

  for (element in module.module.dependenciesList.dependencies) {
    val dependencyExtension = context.javaExtensionService.getDependencyExtension(element) ?: continue
    val scope = dependencyExtension.scope

    if (element is JpsModuleDependency) {
      val dependency = element.moduleReference.resolve()!!
      // todo runtime dependency (getBazelDependencyLabel() is null only because fake "main" modules do not have content roots, and we don't know where to create BUILD file)
      val dependencyModuleDescriptor = context.getKnownModuleDescriptorOrError(dependency)
      val label = context.getBazelDependencyLabel(descriptor = dependencyModuleDescriptor, dependentIsCommunity = module.isCommunity) ?: continue

      addDep(
        isTest = isTest,
        scope = scope,
        deps = deps,
        dependencyLabel = label,
        runtimeDeps = runtimeDeps,
        hasSources = hasSources,
        dependentModule = module.module,
        dependencyModuleDescriptor = dependencyModuleDescriptor,
        exports = exports,
        isExported = dependencyExtension.isExported,
      )

      if (dependency.name == "intellij.libraries.compose.desktop") {
        target.option("plugins", arrayOf("@lib//:compose_plugin"))
      }
    }
    else if (element is JpsLibraryDependency) {
      val untypedLib = element.library!!
      val library = untypedLib.asTyped(JpsRepositoryLibraryType.INSTANCE)
      if (library == null) {
        val files = untypedLib.getPaths(JpsOrderRootType.COMPILED)
        val targetName = camelToSnakeCase(escapeBazelLabel(files.first().nameWithoutExtension))
        val projectBasedDirectoryPath = files.first().relativeTo(if (module.isCommunity) projectDir.resolve("community") else projectDir).parent.invariantSeparatorsPathString
        val bazelLabel = "${if (module.isCommunity) "" else "@community"}//$projectBasedDirectoryPath:$targetName"
        context.libs.add(LocalLibrary(files = files, bazelLabel = bazelLabel, targetName = targetName, isProvided = true, isCommunity = module.isCommunity))
        deps.add(bazelLabel)
        continue
      }

      val data = library.properties.data
      val isModuleLibrary = element.libraryReference.parentReference is JpsModuleReference

      var targetName = camelToSnakeCase(escapeBazelLabel(if (isModuleLibrary) {
        val moduleRef = element.libraryReference.parentReference as JpsModuleReference
        val name = library.name.takeIf { !it.startsWith("#") && it.isNotEmpty() } ?: "${data.artifactId}_${data.version}"
        "${moduleRef.moduleName.removePrefix("intellij.")}_${name}"
      }
      else {
        library.name
      }))

      val isProvided = scope == JpsJavaDependencyScope.PROVIDED
      if (isProvided) {
        targetName += ".provided"
      }

      // we process community modules first, so, `addOrGet` (library equality ignores `isCommunity` flag)
      val isCommunityLibrary = context.libs.addOrGet(
        MavenLibrary(
          mavenCoordinates = "${data.groupId}:${data.artifactId}:${data.version}",
          jars = library.getPaths(JpsOrderRootType.COMPILED),
          sourceJars = library.getPaths(JpsOrderRootType.SOURCES),
          javadocJars = library.getPaths(JpsOrderRootType.DOCUMENTATION),
          targetName = targetName,
          isProvided = isProvided,
          isCommunity = module.isCommunity,
        )
      ).isCommunity

      val libLabel = "${if (isCommunityLibrary) "@lib" else "@ultimate_lib"}//:$targetName"

      addDep(
        isTest = isTest,
        scope = scope,
        deps = deps,
        dependencyLabel = libLabel,
        runtimeDeps = runtimeDeps,
        hasSources = hasSources,
        dependentModule = module.module,
        dependencyModuleDescriptor = null,
        exports = exports,
        isExported = dependencyExtension.isExported,
      )

      if (data.artifactId == "kotlinx-serialization-core-jvm") {
        target.option("plugins", arrayOf("@lib//:serialization_plugin"))
      }
      if (element.libraryReference.libraryName == "jetbrains-jewel-markdown-laf-bridge-styling") {
        target.option("plugins", arrayOf("@lib//:compose_plugin"))
      }
    }
  }

  if (deps.isNotEmpty()) {
    target.option("deps", deps)
  }
  if (exports.isNotEmpty()) {
    target.option("exports", exports)
  }
  if (runtimeDeps.isNotEmpty()) {
    target.option("runtime_deps", runtimeDeps)
  }
}

private fun addDep(
  isTest: Boolean,
  scope: JpsJavaDependencyScope,
  deps: MutableList<String>,
  dependencyLabel: String,
  runtimeDeps: MutableList<String>,
  hasSources: Boolean,
  dependentModule: JpsModule,
  dependencyModuleDescriptor: ModuleDescriptor?,
  exports: MutableList<String>,
  isExported: Boolean,
) {
  if (isExported && !isTest) {
    exports.add(dependencyLabel)
  }

  when {
    isTest -> {
      when {
        scope == JpsJavaDependencyScope.COMPILE -> {
          if (hasSources) {
            deps.add(dependencyLabel)
          }
          else {
            println("WARN: ignoring dependency on $dependencyLabel (module=${dependencyModuleDescriptor?.module?.name})")
          }
        }
        scope == JpsJavaDependencyScope.TEST -> {
          if (dependencyModuleDescriptor == null) {
            deps.add(dependencyLabel)
          }
          else {
            val hasTestSource = dependencyModuleDescriptor.testSources.isNotEmpty()
            if (dependencyModuleDescriptor.sources.isNotEmpty() || !hasTestSource) {
              deps.add(dependencyLabel)
            }
            if (hasTestSource) {
              val testLabel = if (dependencyLabel.contains(':')) {
                "${dependencyLabel}_test"
              }
              else {
                "$dependencyLabel:${dependencyLabel.substringAfterLast('/')}_test"
              }
              deps.add(testLabel)
            }
          }
        }
        scope == JpsJavaDependencyScope.PROVIDED && !hasSources -> {
          if (dependencyModuleDescriptor != null) {
            println("WARN: moduleContent not expected: (moduleContent=${dependencyModuleDescriptor.module.name}, module=${dependentModule.name})")
          }
          deps.add(dependencyLabel)
        }
      }
    }
    scope == JpsJavaDependencyScope.RUNTIME -> {
      runtimeDeps.add(dependencyLabel)
    }
    scope == JpsJavaDependencyScope.COMPILE -> {
      if (hasSources) {
        deps.add(dependencyLabel)
      }
      else {
        if (!isExported) {
          println("WARN: dependency scope for $dependencyLabel should be RUNTIME and not COMPILE (module=$dependentModule)")
        }
        runtimeDeps.add(dependencyLabel)
      }
    }
    scope == JpsJavaDependencyScope.PROVIDED -> {
      // ignore deps if no sources, as `exports` in Bazel means "compile" scope
      if (hasSources) {
        deps.add(dependencyLabel)
      }
      else {
        println("WARN: ignoring dependency on $dependencyLabel (module=$dependentModule)")
      }
    }
  }
}

private val camelCaseToSnakeCasePattern = Regex("(?<=.)[A-Z]")

private fun camelToSnakeCase(s: String): String {
  return when {
    s.startsWith("JUnit") -> "junit" + s.removePrefix("JUnit")
    s.all { it.isUpperCase() } -> s.lowercase()
    else -> s.replace(" ", "").replace("_RC", "_rc").replace(camelCaseToSnakeCasePattern, "_$0").lowercase()
  }
}

private val bazelLabelBadCharsPattern = Regex("[:.+]")

internal fun escapeBazelLabel(name: String): String = bazelLabelBadCharsPattern.replace(name, "_")