// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.JDOMUtil
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class BazelKotlinOptionsTest {
  private fun getOptionValidationError(moduleName: String, options: List<String>, buildBazelContent: String): String? {
    val kotlincOptionsRegex = Regex("""create_kotlinc_options\s*\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)
    val parsedOptions = mutableListOf<String>()
    val match = kotlincOptionsRegex.find(buildBazelContent)
    if (match != null) {
      val lines = match.groupValues[1].lines()
      var l = 0
      while (l < lines.size) {
        if (lines[l].contains('=')) {
          val (key, value) = lines[l].trim(' ', ',').split('=', limit = 2)
          when (key.trim()) {
            "name" if (value.trim() == "True") -> assertThat(value.trim('\"', ',', ' ')).isEqualTo("custom_$moduleName")
            // boolean options
            "x_allow_kotlin_package" if (value.trim() == "True") -> parsedOptions.add("-Xallow-kotlin-package")
            "x_allow_result_return_type" if (value.trim() == "True") -> parsedOptions.add("-Xallow-result-return-type")
            "x_allow_unstable_dependencies" if (value.trim() == "True") -> parsedOptions.add("-Xallow-unstable-dependencies")
            "x_consistent_data_class_copy_visibility" if (value.trim() == "True") -> parsedOptions.add("-Xconsistent-data-class-copy-visibility")
            "x_context_parameters" if (value.trim() == "True") -> parsedOptions.add("-Xcontext-parameters")
            "x_context_receivers" if (value.trim() == "True") -> parsedOptions.add("-Xcontext-receivers")
            "x_inline_classes" if (value.trim() == "True") -> parsedOptions.add("-Xinline-classes")
            "x_no_call_assertions" if (value.trim() == "True") -> parsedOptions.add("-Xno-call-assertions")
            "x_no_param_assertions" if (value.trim() == "True") -> parsedOptions.add("-Xno-param-assertions")
            "x_skip_metadata_version_check" if (value.trim() == "True") -> parsedOptions.add("-Xskip-metadata-version-check")
            "x_skip_prerelease_check" if (value.trim() == "True") -> parsedOptions.add("-Xskip-prerelease-check")
            "x_strict_java_nullability_assertions" if (value.trim() == "True") -> parsedOptions.add("-Xstrict-java-nullability-assertions")
            "x_wasm_attach_js_exception" if (value.trim() == "True") -> parsedOptions.add("-Xwasm-attach-js-exception")
            "x_when_guards" if (value.trim() == "True") -> parsedOptions.add("-Xwhen-guards")
            "x_x_language" -> parsedOptions.add("-XXLanguage:${value.trim(',', ' ', '"')}")
            // non-boolean options
            "opt_in" -> {
              while (!lines[l].contains("]")) {
                if (!lines[l].contains('[')) {
                  parsedOptions.add("-opt-in=${lines[l].trim('"', ',', ' ')}")
                }
                l++
              }
              if (lines[l].contains("=") && lines[l].contains("]")) {
                parsedOptions.add("-opt-in=${lines[l].split("=", limit = 2)[1].trim('[', ']', '"', ',', ' ')}")
              }
            }
            "plugin_options" -> {
              while (!lines[l].contains("]")) {
                if (!lines[l].contains('[')) {
                  parsedOptions.add("-P")
                  parsedOptions.add(lines[l].trim('"', ',', ' '))
                }
                l++
              }
              if (lines[l].contains("=") && lines[l].contains("]")) {
                parsedOptions.add("-P")
                parsedOptions.add(lines[l].split("=", limit = 2)[1].trim('[', ']', '"', ',', ' '))
              }
            }
            "x_explicit_api_mode" -> parsedOptions.add("-Xexplicit-api=${value.trim(',', ' ', '"')}")
            "x_jvm_default" -> parsedOptions.add("-Xjvm-default=${value.trim(',', ' ', '"')}")
            "x_lambdas" -> parsedOptions.add("-Xlambdas=${value.trim(',', ' ', '"')}")
            "x_sam_conversions" -> parsedOptions.add("-Xsam-conversions=${value.trim(',', ' ', '"')}")
          }
        }
        l++
      }
    }
    val parsedOptionsToCheck = (if (!parsedOptions.any { it.startsWith("-Xjvm-default=")}) {
      parsedOptions.plus("-Xjvm-default=all")
    } else {
      parsedOptions
    }).minus("-opt-in=com.intellij.openapi.util.IntellijInternalApi")
    val optionsToCheck = (if (!options.any { it.startsWith("-Xjvm-default=") }) {
      options.plus("-Xjvm-default=all-compatibility")
    } else {
      options
    }).minus("-Xlambdas=indy")
      .map { it.replace("-Xopt-in=", "-opt-in=")}
      .minus("-opt-in=com.intellij.openapi.util.IntellijInternalApi")
      .map { it.replace($$"$MODULE_DIR$/", "")}

    val diff = diffList(optionsToCheck, parsedOptionsToCheck)
    if (diff == null) {
      return null
    }
    return "Kotlin compiler for module $moduleName are invalid:\n$diff"
  }


  fun diffList(expected: List<String>, actual: List<String>): String? {
    if (expected == actual) return null
    val exp = expected.toSet()
    val act = actual.toSet()
    val missing = exp - act
    val extra = act - exp
    val result = StringBuilder()
    if (missing.isNotEmpty()) {
      result.append("Missing options: [${missing.joinToString()}]\n")
    }
    if (extra.isNotEmpty()) {
      result.append("Unexpected: [${extra.joinToString()}]")
    }
    return if (result.isEmpty()) null else result.toString()
  }


  @Test
  fun booleanOptionsValidatorTest() {
    val options = listOf(
      "-Xallow-kotlin-package", // x_allow_kotlin_package
      "-Xallow-result-return-type", // x_allow_result_return_type = True
      "-Xallow-unstable-dependencies", // x_allow_unstable_dependencies = True
      "-Xconsistent-data-class-copy-visibility", // x_consistent_data_class_copy_visibility = True,
      "-Xcontext-parameters", // x_context_parameters = True
      "-Xcontext-receivers", // x_context_receivers = True
      "-Xinline-classes", // x_inline_classes
      "-Xjvm-default=all", // default in IntelliJ project is different from bazel rules
      "-Xno-call-assertions", // x_no_call_assertions = True
      "-Xno-param-assertions", // x_no_param_assertions = True
      "-Xskip-metadata-version-check", // x_skip_metadata_version_check = True
      "-Xskip-prerelease-check", // x_skip_prerelease_check = True
      "-Xstrict-java-nullability-assertions", // x_strict_java_nullability_assertions = True
      "-Xwasm-attach-js-exception", // "x_wasm_attach_js_exception"
      "-Xwhen-guards", // x_when_guards = True
    )
    val content = """
create_kotlinc_options(
  name = "custom_app.fleet",
  x_allow_kotlin_package = True,
  x_allow_result_return_type = True,
  x_allow_unstable_dependencies = True,
  x_consistent_data_class_copy_visibility = True,
  x_context_parameters = True,
  x_context_receivers = True,
  x_inline_classes = True,
  x_no_call_assertions = True,
  x_no_param_assertions = True,
  x_skip_metadata_version_check = True,
  x_skip_prerelease_check = True,
  x_strict_java_nullability_assertions = True,
  x_wasm_attach_js_exception = True,
  x_when_guards = True,
)
"""
    val error = getOptionValidationError("app.fleet", options, content)
    if (error != null) {
      error(error)
    }
  }

  @Test
  fun singleLineOptsValidatorTest()  {
    val options = listOf(
      "-Xexplicit-api=strict", // x_explicit_api_mode = "strict"
      "-Xjvm-default=compatibility", // x_jvm_default = "all"
      "-Xlambdas=class", // x_lambdas = "class",
      "-Xsam-conversions=class", // x_sam_conversions = "class"
      "-opt-in=kotlin.time.ExperimentalTime", // opt-in = [ ]
      "-XXLanguage:+InlineClasses", // x_x_language = "-XXLanguage:+InlineClasses"
      "-P", // plugin_options = [ ]
      "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=-PausableComposition"
    )
    val content = """
create_kotlinc_options(
  name = "custom_app.fleet",
  opt_in = ["kotlin.time.ExperimentalTime"],
  x_explicit_api_mode = "strict",
  x_jvm_default = "compatibility",
  x_lambdas = "class",
  x_sam_conversions = "class",
  x_x_language = "+InlineClasses",
  plugin_options = ["plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=-PausableComposition"],
)
"""
    val error = getOptionValidationError("app.fleet.andel", options, content)
    if (error != null) {
      error(error)
    }
  }


  @Test
  fun multiLineOptionsValidatorTest() {
    val options = listOf(
      "-Xexplicit-api=strict", // x_explicit_api_mode = "strict"
      "-Xjvm-default=compatibility", // x_jvm_default = "compatibility"
      "-Xlambdas=class", // x_lambdas = "class",
      "-Xsam-conversions=class", // x_sam_conversions = "class"
      "-opt-in=kotlin.ExperimentalStdlibApi", // opt-in = [ ]
      "-opt-in=kotlin.time.ExperimentalTime", // opt-in = [ ]
      "-XXLanguage:+InlineClasses", // x_x_language = "-XXLanguage:+InlineClasses"
      "-P", // plugin_options = [ ]
      "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=-PausableComposition"
    )
    val content = """
create_kotlinc_options(
  name = "custom_app.fleet",
  opt_in = [
    "kotlin.time.ExperimentalTime",
    "kotlin.ExperimentalStdlibApi",
  ],
  x_explicit_api_mode = "strict",
  x_jvm_default = "compatibility",
  x_lambdas = "class",
  x_sam_conversions = "class",
  x_x_language = "+InlineClasses",
  plugin_options = [
    "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=-PausableComposition"
  ],
)
"""
    val error = getOptionValidationError("app.fleet.andel", options, content)
    if (error != null) {
      error(error)
    }
  }

  @Test
  fun verifyAllCompilerOptionsFromFacetPresetInBuildBazel() {
    val skippedModules = listOf(
      "fleet.plugins.gradle.backend.plugin", // TODO: investigate
      "fleet.plugins.java.backend.plugin", // TODO: investigate
      "fleet.plugins.maven.backend.plugin", // TODO: investigate
      "intellij.bazel.build",
      "intellij.bazel.commons",
      "intellij.bazel.plugin",
      "intellij.bazel.protobuf",
      "intellij.bazel.sdkcompat",
      "intellij.bazel.sdkcompat.k2",
      "intellij.bazel.server",
      "intellij.dotenv.docker",
      "intellij.dotenv.go",
      "intellij.dotenv.php",
      "intellij.dotenv.ruby",
      "intellij.dotenv.tests",
      "intellij.gradle.toolingExtension.tests",
      "intellij.idea.ultimate.build", // TODO: investigate
      "intellij.platform.rpc.split", //TODO: Investigate
    )
    val m2Repo = Path.of(System.getProperty("user.home"), ".m2/repository")
    val project = JpsSerializationManager.getInstance().loadProject(
      PathManager.getHomePath(),
      mapOf("MAVEN_REPOSITORY" to m2Repo.toString()), true
    )
    val errors = mutableListOf<String>()
    val uniqueOptions = mutableSetOf<String>()
    for (module in project.modules.filter { !skippedModules.contains(it.name) }) {
      val imlDir = JpsModelSerializationDataService.getBaseDirectory(module)?.toPath() ?: error("Cannot find base directory for module ${module.name}")
      val imlFile = imlDir.resolve("${module.name}.iml")
      val buildBazelFile = imlDir.resolve("BUILD.bazel")
      if (!imlFile.isRegularFile()) {
        errors.add("iml file for module ${module.name} not found in path: $imlFile")
      }
      if (!buildBazelFile.isRegularFile()) {
        if (imlFile.readText().contains("<facet type=\"kotlin-language\" name=\"Kotlin\">")) {
          errors.add("bazel file for module ${module.name} not found in path: $buildBazelFile")
        }
      }
      val moduleXml = JDOMUtil.load(imlFile)

      val compilerArgs = moduleXml
                           .getChildren("component")
                           .singleOrNull { it.getAttributeValue("name") == "FacetManager" }
                           ?.getChildren("facet")
                           ?.singleOrNull { it.getAttributeValue("type") == "kotlin-language" }
                           ?.getChildren("configuration")
                           ?.single()
                           ?.getChildren("compilerSettings")
                           ?.single()
                           ?.getChildren("option")
                           ?.single { it.getAttributeValue("name") == "additionalArguments" }
                           ?.getAttributeValue("value") ?: continue
      val options = compilerArgs.trim().split(" ").filter { it.isNotBlank() }
      var i = 0
      val imlCompilerArgs = mutableListOf<String>()
      imlCompilerArgs.addAll(options)
      while (i < options.size) {
        val option = StringBuilder(options[i])
        if (options[i].startsWith("-P") && i + 1 < options.size) {
          option.append(" ").append(options[i + 1])
          i++
        }
        val optionString = option.toString()
        uniqueOptions.add(optionString)
        i++
      }
      val error = getOptionValidationError(module.name, imlCompilerArgs, buildBazelFile.readText())
      if (error != null) {
        errors.add(error)
      }
    }
    // Uncomment if you want to see all unique options
    // println("Unique options: \n\t${uniqueOptions.sorted().joinToString("\n\t")}")
    if (errors.isNotEmpty()) {
      error(errors.joinToString("\n"))
    }
  }
}