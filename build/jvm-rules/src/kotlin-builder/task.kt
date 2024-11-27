package org.jetbrains.bazel.jvm.kotlin

import java.nio.file.Path

enum class RuleKind {
  LIBRARY,
  BINARY,
  TEST,
  IMPORT
}

enum class Platform {
  JVM, UNRECOGNIZED
}

data class CompilationTaskInfo(
  @JvmField val label: String,
  @JvmField val platform: Platform,
  @JvmField val ruleKind: RuleKind,
  @JvmField val moduleName: String,
)

internal data class JvmCompilationTask(
  @JvmField val workingDir: Path,

  @JvmField val args: ArgMap<KotlinBuilderFlags>,

  @JvmField val info: CompilationTaskInfo,
  @JvmField val outputs: Outputs,
  @JvmField val inputs: Inputs,

  @JvmField val outJar: Path,
)

data class Outputs(
  @JvmField val jdeps: Path?,
  @JvmField val srcjar: Path?,
  @JvmField val abiJar: Path?,
)

data class Inputs(
  @JvmField val classpath: List<Path>,
  @JvmField val directDependencies: List<String>,

  @JvmField val processors: List<String>,
  @JvmField val processorPaths: List<String>,
  @JvmField val stubsPluginOptions: List<String>,
  @JvmField val stubsPlugins: List<String> = emptyList(),
  @JvmField val stubsPluginClasspath: List<String>,
  @JvmField val compilerPlugins: List<String> = emptyList(),
  @JvmField val compilerPluginClasspath: List<Path>,
  @JvmField val javacFlags: List<String> = emptyList(),
  @JvmField val depsArtifacts: List<String>,
)

