// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import java.nio.file.Path

internal data class KotlincProjectDefaults(
  val jvmTarget: String,
  val apiVersion: String,
  val languageVersion: String,
  val optIn: List<String>,
  val progressive: Boolean,
  val jvmDefault: String,
  val rawJvmDefault: String?,
  val diagnosticNames: Boolean,
  val allWarnings: Boolean,
  val xxLanguage: List<String>,
)

internal fun parseKotlincProjectDefaults(projectDir: Path): KotlincProjectDefaults =
  parseKotlincProjectDefaultsFromXml(projectDir.resolve(".idea/kotlinc.xml"))

private const val OPT_IN_PREFIX = "-opt-in="
private const val PROGRESSIVE_FLAG = "-progressive"
private const val XJVM_DEFAULT_PREFIX = "-Xjvm-default="
private const val DIAGNOSTIC_NAMES_FLAG = "-Xrender-internal-diagnostic-names"
private const val ALL_WARNINGS_FLAG = "-Xreport-all-warnings"
private const val XX_LANGUAGE_PREFIX = "-XXLanguage:"

/**
 * Every component name allowed at `<project>` level in `kotlinc.xml`, mapped to the set of `<option name="...">`
 * names allowed within it. Components not listed here cause a hard fail; options whose name is not in the
 * matching set also cause a hard fail.
 */
private val KNOWN_COMPONENTS: Map<String, Set<String>> = mapOf(
  "Kotlin2JsCompilerArguments" to setOf("moduleKind"),
  "Kotlin2JvmCompilerArguments" to setOf("jvmTarget"),
  "KotlinCommonCompilerArguments" to setOf("apiVersion", "languageVersion"),
  "KotlinCompilerSettings" to setOf("additionalArguments"),
  "KotlinJpsPluginSettings" to setOf("version"),
)

private val ALLOWED_COMPONENT_ATTRIBUTES = setOf("name")
private val ALLOWED_OPTION_ATTRIBUTES = setOf("name", "value")

internal fun parseKotlincProjectDefaultsFromXml(kotlincXml: Path): KotlincProjectDefaults {
  val xml = JDOMUtil.load(kotlincXml)
  validateKotlincXmlStructure(xml, kotlincXml)

  val jvmComponent = requireComponent(xml, "Kotlin2JvmCompilerArguments", kotlincXml)
  val commonComponent = requireComponent(xml, "KotlinCommonCompilerArguments", kotlincXml)
  val compilerSettings = requireComponent(xml, "KotlinCompilerSettings", kotlincXml)
  requireComponent(xml, "KotlinJpsPluginSettings", kotlincXml)

  val jvmTarget = requireOption(jvmComponent, "jvmTarget", "Kotlin2JvmCompilerArguments", kotlincXml)
  val apiVersion = requireOption(commonComponent, "apiVersion", "KotlinCommonCompilerArguments", kotlincXml)
  val languageVersion = requireOption(commonComponent, "languageVersion", "KotlinCommonCompilerArguments", kotlincXml)
  val additionalArguments = requireOption(compilerSettings, "additionalArguments", "KotlinCompilerSettings", kotlincXml)

  val optIn = mutableListOf<String>()
  var progressive = false
  var rawJvmDefault: String? = null
  var diagnosticNames = false
  var allWarnings = false
  val xxLanguage = mutableListOf<String>()

  for (token in additionalArguments.split(" ").filter { it.isNotEmpty() }) {
    when {
      token.startsWith(OPT_IN_PREFIX) -> optIn += token.removePrefix(OPT_IN_PREFIX)
      token == PROGRESSIVE_FLAG -> progressive = true
      token.startsWith(XJVM_DEFAULT_PREFIX) -> {
        val value = token.removePrefix(XJVM_DEFAULT_PREFIX)
        check(rawJvmDefault == null) {
          "Duplicate $XJVM_DEFAULT_PREFIX in $kotlincXml KotlinCompilerSettings.additionalArguments"
        }
        rawJvmDefault = value
      }
      token == DIAGNOSTIC_NAMES_FLAG -> diagnosticNames = true
      token == ALL_WARNINGS_FLAG -> allWarnings = true
      token.startsWith(XX_LANGUAGE_PREFIX) -> xxLanguage += token.removePrefix(XX_LANGUAGE_PREFIX)
      else -> error(
        "Unsupported Kotlin compiler option in $kotlincXml KotlinCompilerSettings.additionalArguments: '$token'. " +
        "To support it, extend parseKotlincProjectDefaults() and the create_kotlinc_options template in CompilerOptionsBzlGenerator."
      )
    }
  }

  val jvmDefault = if (rawJvmDefault != null) normalizeLegacyJvmDefault(rawJvmDefault, kotlincXml) else "no-compatibility"

  return KotlincProjectDefaults(
    jvmTarget = jvmTarget,
    apiVersion = apiVersion,
    languageVersion = languageVersion,
    optIn = optIn.toList(),
    progressive = progressive,
    jvmDefault = jvmDefault,
    rawJvmDefault = rawJvmDefault,
    diagnosticNames = diagnosticNames,
    allWarnings = allWarnings,
    xxLanguage = xxLanguage.toList(),
  )
}

private fun validateKotlincXmlStructure(root: Element, kotlincXml: Path) {
  val seenComponents = mutableSetOf<String>()
  for (component in root.getChildren("component")) {
    val componentName = component.getAttributeValue("name")
                        ?: error("<component> in $kotlincXml is missing the 'name' attribute")

    val knownOptions = KNOWN_COMPONENTS[componentName]
                       ?: error(
                         "Unsupported component '$componentName' in $kotlincXml. " +
                         "To support it, extend KNOWN_COMPONENTS in parseKotlincProjectDefaults()."
                       )

    check(seenComponents.add(componentName)) {
      "Duplicate <component name=\"$componentName\"> in $kotlincXml"
    }

    rejectUnknownAttributes(
      element = component,
      elementDescription = "<component name=\"$componentName\">",
      allowed = ALLOWED_COMPONENT_ATTRIBUTES,
      kotlincXml = kotlincXml,
    )

    val seenOptions = mutableSetOf<String>()
    for (option in component.getChildren("option")) {
      val optionName = option.getAttributeValue("name")
                       ?: error("<option> in <component name=\"$componentName\"> in $kotlincXml is missing the 'name' attribute")
      if (optionName !in knownOptions) {
        error(
          "Unsupported option '$optionName' in $componentName in $kotlincXml. " +
          "To support it, extend parseKotlincProjectDefaults() and the create_kotlinc_options template in CompilerOptionsBzlGenerator."
        )
      }
      check(seenOptions.add(optionName)) {
        "Duplicate <option name=\"$optionName\"> in <component name=\"$componentName\"> in $kotlincXml"
      }
      rejectUnknownAttributes(
        element = option,
        elementDescription = "<option name=\"$optionName\"> in <component name=\"$componentName\">",
        allowed = ALLOWED_OPTION_ATTRIBUTES,
        kotlincXml = kotlincXml,
      )
    }
  }
}

private fun rejectUnknownAttributes(element: Element, elementDescription: String, allowed: Set<String>, kotlincXml: Path) {
  for (attribute in element.attributes) {
    if (attribute.name !in allowed) {
      error(
        "Unsupported attribute '${attribute.name}' on $elementDescription in $kotlincXml. " +
        "To support it, extend parseKotlincProjectDefaults()."
      )
    }
  }
}

private fun normalizeLegacyJvmDefault(rawValue: String, kotlincXml: Path): String = when (rawValue) {
  "disable" -> "disable"
  "all-compatibility" -> "enable"
  "all" -> "no-compatibility"
  else -> error(
    "Unsupported -Xjvm-default value '$rawValue' in $kotlincXml KotlinCompilerSettings.additionalArguments. " +
    "To support it, extend parseKotlincProjectDefaults() and the create_kotlinc_options template in CompilerOptionsBzlGenerator."
  )
}

private fun requireComponent(root: Element, componentName: String, kotlincXml: Path): Element =
  root.getChildren("component").singleOrNull { it.getAttributeValue("name") == componentName }
  ?: error("$componentName component not found in $kotlincXml")

private fun requireOption(component: Element, optionName: String, componentName: String, kotlincXml: Path): String {
  val option = component.getChildren("option").singleOrNull { it.getAttributeValue("name") == optionName }
               ?: error("$optionName option not found in $componentName in $kotlincXml")
  return option.getAttributeValue("value")
         ?: error("$optionName option in $componentName in $kotlincXml is missing the 'value' attribute")
}
