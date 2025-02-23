// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.bazel.kotlin.plugin.jdeps

import com.google.devtools.build.lib.view.proto.Deps
import org.jetbrains.bazel.jvm.kotlin.JarOwner
import org.jetbrains.bazel.jvm.kotlin.JarOwner.Companion.INJECTING_RULE_KIND
import org.jetbrains.bazel.jvm.kotlin.JarOwner.Companion.TARGET_LABEL
import org.jetbrains.intellij.build.io.writeFileUsingTempFile
import org.jetbrains.kotlin.codegen.ClassFileFactory
import org.jetbrains.kotlin.codegen.extensions.ClassFileFactoryFinalizerExtension
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

internal class JdepsGenExtension(
  private val classUsageRecorder: ClassUsageRecorder,
  private val configuration: CompilerConfiguration,
) : ClassFileFactoryFinalizerExtension {
  override fun finalizeClassFactory(factory: ClassFileFactory) {
    onAnalysisCompleted(
      configuration = configuration,
      explicitClassesCanonicalPaths = classUsageRecorder.explicitClassesCanonicalPaths,
      implicitClassesCanonicalPaths = classUsageRecorder.implicitClassesCanonicalPaths,
    )
  }
}

private fun onAnalysisCompleted(
  explicitClassesCanonicalPaths: Set<String>,
  implicitClassesCanonicalPaths: Set<String>,
  configuration: CompilerConfiguration,
) {
  val directDeps: List<String> = configuration.getList(JdepsGenConfigurationKeys.DIRECT_DEPENDENCIES)
  val targetLabel = configuration.getNotNull(JdepsGenConfigurationKeys.TARGET_LABEL)
  val explicitDeps = createDepsMap(explicitClassesCanonicalPaths)

  doWriteJdeps(
    directDeps = directDeps,
    targetLabel = targetLabel,
    explicitDeps = explicitDeps,
    implicitClassesCanonicalPaths = implicitClassesCanonicalPaths,
    configuration = configuration,
  )

  doStrictDeps(
    compilerConfiguration = configuration,
    targetLabel = targetLabel,
    directDeps = directDeps,
    explicitDeps = explicitDeps,
  )
}

/**
 * Returns a map of jars to classes loaded from those jars.
 */
private fun createDepsMap(classes: Set<String>): Map<String, List<String>> {
  val jarsToClasses = HashMap<String, MutableList<String>>()
  for (aClass in classes) {
    val parts = aClass.split("!/")
    val jarPath = parts[0]
    if (jarPath.endsWith(".jar")) {
      jarsToClasses.computeIfAbsent(jarPath) { ArrayList() }.add(parts[1])
    }
  }
  return jarsToClasses
}

private fun doWriteJdeps(
  directDeps: List<String>,
  targetLabel: String,
  explicitDeps: Map<String, List<String>>,
  implicitClassesCanonicalPaths: Set<String>,
  configuration: CompilerConfiguration,
) {
  val implicitDeps = createDepsMap(implicitClassesCanonicalPaths)

  val deps = mutableListOf<Deps.Dependency>()

  val unusedDeps = directDeps.subtract(explicitDeps.keys)
  for (jarPath in unusedDeps) {
    val dependency = Deps.Dependency.newBuilder()
    dependency.kind = Deps.Dependency.Kind.UNUSED
    dependency.path = jarPath
    deps.add(dependency.build())
  }

  for ((jarPath, _) in explicitDeps) {
    val dependency = Deps.Dependency.newBuilder()
    dependency.kind = Deps.Dependency.Kind.EXPLICIT
    dependency.path = jarPath
    deps.add(dependency.build())
  }

  for (path in implicitDeps.keys.subtract(explicitDeps.keys)) {
    val dependency = Deps.Dependency.newBuilder()
    dependency.kind = Deps.Dependency.Kind.IMPLICIT
    dependency.path = path
    deps.add(dependency.build())
  }

  val rootBuilder = Deps.Dependencies.newBuilder()
  rootBuilder.success = true
  rootBuilder.ruleLabel = targetLabel

  deps.sortBy { it.path }
  rootBuilder.addAllDependency(deps)
  // build and write out deps.proto
  val jdepsOutput = Path.of(configuration.getNotNull(JdepsGenConfigurationKeys.OUTPUT_JDEPS))
  val data = rootBuilder.build().toByteArray()
  writeFileUsingTempFile(jdepsOutput) {
    Files.write(it, data)
  }
}

private fun doStrictDeps(
  compilerConfiguration: CompilerConfiguration,
  targetLabel: String,
  directDeps: List<String>,
  explicitDeps: Map<String, List<String>>,
) {
  when (compilerConfiguration.get(JdepsGenConfigurationKeys.STRICT_KOTLIN_DEPS, "none")) {
    "warn" -> checkStrictDeps(explicitDeps, directDeps, targetLabel)
    "error" -> {
      require(!checkStrictDeps(explicitDeps, directDeps, targetLabel)) {
        "Strict Deps Violations - please fix"
      }
    }
  }
}

private fun readJarOwnerFromManifest(jarPath: Path): JarOwner {
  JarFile(jarPath.toFile()).use { jarFile ->
    val manifest = jarFile.manifest ?: return JarOwner(jarPath)
    val attributes = manifest.mainAttributes
    val label = attributes[TARGET_LABEL] as String? ?: return JarOwner(jarPath)
    val injectingRuleKind = attributes[INJECTING_RULE_KIND] as String?
    return JarOwner(jar = jarPath, label = label, aspect = injectingRuleKind)
  }
}

/**
 * Prints strict deps warnings and returns true if violations were found.
 */
private fun checkStrictDeps(
  result: Map<String, List<String>>,
  directDeps: List<String>,
  targetLabel: String,
): Boolean {
  val missingStrictDeps = result.keys
    .asSequence()
    .filter { !directDeps.contains(it) }
    .map { readJarOwnerFromManifest(Path.of(it)) }
    .toList()

  if (missingStrictDeps.isEmpty()) {
    return false
  }

  val missingStrictLabels = missingStrictDeps.mapNotNull { it.label }

  val open = "\u001b[35m\u001b[1m"
  val close = "\u001b[0m"

  var command =
    """
    $open ** Please add the following dependencies:$close
    ${
      missingStrictDeps.map { it.label ?: it.jar }.joinToString(" ")
    } to $targetLabel
    """

  if (missingStrictLabels.isNotEmpty()) {
    command += """$open ** You can use the following buildozer command:$close
    buildozer 'add deps ${
      missingStrictLabels.joinToString(" ")
    }' $targetLabel
    """
  }

  println(command.trimIndent())
  return true
}