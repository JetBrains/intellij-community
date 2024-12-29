// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm

import com.google.devtools.build.lib.view.proto.Deps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarFile

/**
 * Persistent worker capable command line program for merging multiple Jdeps files into a single
 * file.
 */
private val TARGET_LABEL = Attributes.Name("Target-Label")

private fun readJarOwnerFromManifest(jarPath: Path): String? {
  return JarFile(jarPath.toFile()).use { jarFile ->
    jarFile.manifest ?: return null
  }.mainAttributes.getValue(TARGET_LABEL)
}

internal suspend fun mergeJdeps(
  consoleOutput: Writer,
  label: String,
  inputs: Sequence<Path>,
  output: Path,
  reportUnusedDeps: String,
): Int {
  val rootBuilder = Deps.Dependencies.newBuilder()
  rootBuilder.success = false
  rootBuilder.ruleLabel = label

  val dependencyMap = HashMap<String, Deps.Dependency>()
  for (input in inputs) {
    val deps = Deps.Dependencies.parseFrom(Files.readAllBytes(input))
    for (dep in deps.dependencyList) {
      // replace dependency if it has a stronger kind than one we encountered before,
      // for example, if new kind is EXPLICIT(0), and old kind is UNUSED(2), we should use dep with EXPLICIT(0)
      dependencyMap.merge(dep.path, dep) { old, new -> if (new.kind < old.kind) new else old }
    }
  }

  val dependencies = dependencyMap.values.sortedBy { it.path }
  rootBuilder.addAllDependency(dependencies)
  rootBuilder.success = true

  withContext(Dispatchers.IO) {
    Files.write(output, rootBuilder.build().toByteArray())
  }

  if (reportUnusedDeps == "off") {
    return 0
  }

  val kindMap = LinkedHashMap<String, Deps.Dependency.Kind>()

  // a target might produce multiple jars (Android produces `_resources.jar`),
  // so we need to make sure we don't mart the dependency as unused unless all the jars are unused
  for (dep in dependencies) {
    var label = readJarOwnerFromManifest(Path.of(dep.path)) ?: continue
    if (label.startsWith("@@") || label.startsWith("@/")) {
      label = label.substring(1)
    }
    if (kindMap.getOrDefault(label, Deps.Dependency.Kind.UNUSED) >= dep.kind) {
      kindMap.put(label, dep.kind)
    }
  }

  val unusedLabels = kindMap.entries
    .asSequence()
    .filter { it.value == Deps.Dependency.Kind.UNUSED }
    .map { it.key }
    .filter { it != label }
    .toList()

  if (unusedLabels.isNotEmpty()) {
    val open = "\u001b[35m\u001b[1m"
    val close = "\u001b[0m"
    val message = """
    |$open ** Please remove the following dependencies:$close ${
      unusedLabels.joinToString(
        " ",
      )
    } from $label 
    |$open ** You can use the following buildozer command:$close buildozer 'remove deps ${
      unusedLabels.joinToString(" ")
    }' $label
    """.trimMargin()
    consoleOutput.append(message)
  }
  return if (reportUnusedDeps == "error") 1 else 0
}
