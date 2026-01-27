// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.benchmark.generator

import com.intellij.tools.build.bazel.benchmark.runner.Language
import com.intellij.tools.build.bazel.benchmark.runner.ProjectConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

/**
 * Generates synthetic test projects for benchmarking.
 *
 * Creates projects with configurable:
 * - Language (Java, Kotlin, or mixed)
 * - Number of files
 * - Distribution across API, implementation, and utility classes
 * - Internal dependencies
 */
class ProjectGenerator(
  private val outputDir: Path,
) {

  /**
   * Generates a synthetic project with the given configuration.
   *
   * @return Path to the generated project root
   */
  fun generate(config: ProjectConfig): Path {
    val projectPath = outputDir.resolve(config.name)

    // Clean up existing project
    if (Files.exists(projectPath)) {
      projectPath.toFile().deleteRecursively()
    }

    // Create directory structure
    Files.createDirectories(projectPath.resolve("src/api"))
    Files.createDirectories(projectPath.resolve("src/impl"))
    Files.createDirectories(projectPath.resolve("src/util"))

    // Generate source files
    val apiFiles = generateApiClasses(projectPath, config)
    val utilFiles = generateUtilClasses(projectPath, config)
    val implFiles = generateImplClasses(projectPath, config, apiFiles, utilFiles)

    // Generate BUILD.bazel
    generateBuildFile(projectPath, config, apiFiles + implFiles + utilFiles)

    println("Generated project '${config.name}' with ${config.totalFiles} files at $projectPath")

    return projectPath
  }

  private fun generateApiClasses(projectPath: Path, config: ProjectConfig): List<GeneratedFile> {
    val files = mutableListOf<GeneratedFile>()

    repeat(config.apiClassCount) { index ->
      val className = "Api%03d".format(index + 1)
      val extension = config.language.getExtensionFor(index)
      val fileName = "$className.$extension"
      val filePath = projectPath.resolve("src/api/$fileName")

      val content = if (extension == "kt") {
        generateKotlinApiClass(className)
      } else {
        generateJavaApiClass(className)
      }

      Files.writeString(filePath, content)
      files.add(GeneratedFile(className, "api/$fileName", FileType.API))
    }

    return files
  }

  private fun generateUtilClasses(projectPath: Path, config: ProjectConfig): List<GeneratedFile> {
    val files = mutableListOf<GeneratedFile>()

    repeat(config.utilClassCount) { index ->
      val className = "Util%03d".format(index + 1)
      val extension = config.language.getExtensionFor(index)
      val fileName = "$className.$extension"
      val filePath = projectPath.resolve("src/util/$fileName")

      val content = if (extension == "kt") {
        generateKotlinUtilClass(className)
      } else {
        generateJavaUtilClass(className)
      }

      Files.writeString(filePath, content)
      files.add(GeneratedFile(className, "util/$fileName", FileType.UTIL))
    }

    return files
  }

  private fun generateImplClasses(
    projectPath: Path,
    config: ProjectConfig,
    apiFiles: List<GeneratedFile>,
    utilFiles: List<GeneratedFile>,
  ): List<GeneratedFile> {
    val files = mutableListOf<GeneratedFile>()
    val random = Random(42) // Fixed seed for reproducibility

    repeat(config.implClassCount) { index ->
      val className = "Impl%03d".format(index + 1)
      val extension = config.language.getExtensionFor(index)
      val fileName = "$className.$extension"
      val filePath = projectPath.resolve("src/impl/$fileName")

      // Select random API classes to depend on
      val apiDeps = if (apiFiles.isNotEmpty()) {
        apiFiles.shuffled(random).take(config.avgDepsPerClass.coerceAtMost(apiFiles.size))
      } else emptyList()

      // Select random Util classes to depend on
      val utilDeps = if (utilFiles.isNotEmpty()) {
        utilFiles.shuffled(random).take((config.avgDepsPerClass / 2).coerceAtMost(utilFiles.size))
      } else emptyList()

      val content = if (extension == "kt") {
        generateKotlinImplClass(className, apiDeps, utilDeps)
      } else {
        generateJavaImplClass(className, apiDeps, utilDeps)
      }

      Files.writeString(filePath, content)
      files.add(GeneratedFile(className, "impl/$fileName", FileType.IMPL))
    }

    return files
  }

  private fun generateBuildFile(projectPath: Path, config: ProjectConfig, files: List<GeneratedFile>) {
    val srcs = files.map { "src/${it.path}" }

    val content = buildString {
      appendLine("load(\"@rules_kotlin//kotlin:jvm.bzl\", \"kt_jvm_library\")")
      appendLine()
      appendLine("kt_jvm_library(")
      appendLine("    name = \"${config.name}\",")
      appendLine("    srcs = [")
      srcs.forEach { src ->
        appendLine("        \"$src\",")
      }
      appendLine("    ],")
      appendLine("    visibility = [\"//visibility:public\"],")
      appendLine(")")
    }

    Files.writeString(projectPath.resolve("BUILD.bazel"), content)
  }

  // Kotlin code generators

  private fun generateKotlinApiClass(className: String): String = """
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package benchmark.api

/**
 * API class $className for benchmark testing.
 */
interface $className {
  fun process(input: String): String
  fun validate(data: Any): Boolean
  fun getIdentifier(): String
}
""".trimIndent()

  private fun generateKotlinUtilClass(className: String): String = """
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package benchmark.util

/**
 * Utility class $className for benchmark testing.
 */
object $className {
  fun formatString(input: String): String = input.trim().uppercase()
  fun validateNotEmpty(value: String): Boolean = value.isNotEmpty()
  fun generateId(): String = "${className}_" + System.nanoTime()
}
""".trimIndent()

  private fun generateKotlinImplClass(
    className: String,
    apiDeps: List<GeneratedFile>,
    utilDeps: List<GeneratedFile>,
  ): String {
    val apiImports = apiDeps.joinToString("\n") { "import benchmark.api.${it.className}" }
    val utilImports = utilDeps.joinToString("\n") { "import benchmark.util.${it.className}" }

    val implements = if (apiDeps.isNotEmpty()) " : ${apiDeps.first().className}" else ""

    val apiUsage = if (apiDeps.size > 1) {
      apiDeps.drop(1).mapIndexed { i, dep ->
        "  private val dep$i: ${dep.className}? = null"
      }.joinToString("\n")
    } else ""

    val utilUsage = utilDeps.joinToString("\n") { dep ->
      "  private val ${dep.className.lowercase()}Result = ${dep.className}.generateId()"
    }

    return """
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package benchmark.impl

$apiImports
$utilImports

/**
 * Implementation class $className for benchmark testing.
 */
class $className$implements {
$apiUsage
$utilUsage

  override fun process(input: String): String {
    return "Processed: ${'$'}input by $className"
  }

  override fun validate(data: Any): Boolean {
    return data.toString().isNotEmpty()
  }

  override fun getIdentifier(): String {
    return "$className"
  }

  private fun internalHelper(): String {
    return "Helper for $className"
  }
}
""".trimIndent()
  }

  // Java code generators

  private fun generateJavaApiClass(className: String): String = """
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package benchmark.api;

/**
 * API class $className for benchmark testing.
 */
public interface $className {
  String process(String input);
  boolean validate(Object data);
  String getIdentifier();
}
""".trimIndent()

  private fun generateJavaUtilClass(className: String): String = """
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package benchmark.util;

/**
 * Utility class $className for benchmark testing.
 */
public final class $className {
  private $className() {}

  public static String formatString(String input) {
    return input.trim().toUpperCase();
  }

  public static boolean validateNotEmpty(String value) {
    return value != null && !value.isEmpty();
  }

  public static String generateId() {
    return "${className}_" + System.nanoTime();
  }
}
""".trimIndent()

  private fun generateJavaImplClass(
    className: String,
    apiDeps: List<GeneratedFile>,
    utilDeps: List<GeneratedFile>,
  ): String {
    val apiImports = apiDeps.joinToString("\n") { "import benchmark.api.${it.className};" }
    val utilImports = utilDeps.joinToString("\n") { "import benchmark.util.${it.className};" }

    val implements = if (apiDeps.isNotEmpty()) " implements ${apiDeps.first().className}" else ""

    val apiFields = if (apiDeps.size > 1) {
      apiDeps.drop(1).mapIndexed { i, dep ->
        "  private ${dep.className} dep$i;"
      }.joinToString("\n")
    } else ""

    val utilFields = utilDeps.joinToString("\n") { dep ->
      "  private final String ${dep.className.lowercase()}Result = ${dep.className}.generateId();"
    }

    return """
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package benchmark.impl;

$apiImports
$utilImports

/**
 * Implementation class $className for benchmark testing.
 */
public class $className$implements {
$apiFields
$utilFields

  @Override
  public String process(String input) {
    return "Processed: " + input + " by $className";
  }

  @Override
  public boolean validate(Object data) {
    return data != null && !data.toString().isEmpty();
  }

  @Override
  public String getIdentifier() {
    return "$className";
  }

  private String internalHelper() {
    return "Helper for $className";
  }
}
""".trimIndent()
  }
}

/**
 * Represents a generated source file.
 */
data class GeneratedFile(
  val className: String,
  val path: String,
  val type: FileType,
)

/**
 * Type of generated file.
 */
enum class FileType {
  API,
  IMPL,
  UTIL,
}
