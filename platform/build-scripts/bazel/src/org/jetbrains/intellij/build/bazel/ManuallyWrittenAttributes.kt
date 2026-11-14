// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Reads and keeps the manually written attributes and the rule names of the targets of a BUILD file. */
internal class ManuallyWrittenAttributes(private val bazelFilesLoader: BazelFilesLoader) {
  private val parsedBuildFiles = ConcurrentHashMap<Path, ParsedBuildFile>()

  /** Returns the manually written [attribute] of the target [targetName] in the BUILD file of [module]. */
  fun getManuallyWrittenAttributes(
    module: ModuleDescriptor,
    attribute: ManuallyWrittenAttributeDefinition,
    targetName: String = module.targetName,
  ): List<ManuallyWrittenAttribute> {
    return getParsedBuildFile(module).attributes.filter {
      it.targetName == targetName && attribute.matches(ruleName = it.targetType, attributeName = it.attributeName)
    }
  }

  /**
   * Returns the rule name of the target of [module] in the existing BUILD file, for example `ij_plugin_module`.
   * Returns `null` if the BUILD file or the target does not exist.
   */
  fun getExistingTargetType(module: ModuleDescriptor): String? {
    return getParsedBuildFile(module).targetTypes.get(module.targetName)
  }

  private fun getParsedBuildFile(module: ModuleDescriptor): ParsedBuildFile {
    val bazelFile = module.bazelBuildFile
    return parsedBuildFiles.getOrPut(bazelFile) {
      parseBuildFile(bazelFile, bazelFilesLoader.getBuildFileContent(bazelFile))
    }
  }
}

internal class ParsedBuildFile(
  @JvmField val attributes: List<ManuallyWrittenAttribute>,
  /** Maps the name of a target to the name of its rule. */
  @JvmField val targetTypes: Map<String, String>,
)

internal class ManuallyWrittenAttribute(
  val targetName: String,
  val targetType: String,
  val attributeName: String,
  val attributeValue: String,
)

internal fun Target.insertManuallyWrittenAttributes(manuallyWrittenAttributes: List<ManuallyWrittenAttribute>) {
  manuallyWrittenAttributes.forEach { attribute ->
    option(attribute.attributeName, ManuallyWrittenAttributeValue(attribute.attributeValue))
  }
}

/**
 * An attribute that `jps-to-bazel` keeps in a BUILD file, because a developer writes it manually.
 * Another rule can have an attribute with the same name that the generator writes. So the definition also names the rules.
 */
internal class ManuallyWrittenAttributeDefinition(
  val attributeName: String,
  /** The names of the rules whose targets can have the manually written attribute. */
  val ruleNames: Set<String>,
) {
  fun matches(ruleName: String, attributeName: String): Boolean = attributeName == this.attributeName && ruleName in ruleNames
}

internal val DATA_ATTRIBUTE = ManuallyWrittenAttributeDefinition("data", ruleNames = setOf(JVM_LIBRARY_RULE, IJ_PLUGIN_MODULE_RULE))
internal val NON_CLASSPATH_DATA_ATTRIBUTE = ManuallyWrittenAttributeDefinition("non_classpath_data", ruleNames = setOf(IJ_PLUGIN_MODULE_RULE))
internal val DIRECTORY_NAME_ATTRIBUTE = ManuallyWrittenAttributeDefinition("directory_name", ruleNames = setOf(IJ_PLUGIN_RULE))
private val MANUALLY_WRITTEN_ATTRIBUTES = listOf(DATA_ATTRIBUTE, NON_CLASSPATH_DATA_ATTRIBUTE, DIRECTORY_NAME_ATTRIBUTE)

private val FUNCTION_CALL_REGEX = Regex("""([a-zA-Z_][a-zA-Z0-9_]*)\(""")
private val ATTRIBUTE_ASSIGNMENT_REGEX = Regex(""" *([a-zA-Z_][a-zA-Z0-9_]*) = (.*)""")

internal fun parseBuildFile(bazelFile: Path, fileContent: String?): ParsedBuildFile {
  val lines = fileContent?.lineSequence() ?: return ParsedBuildFile(attributes = emptyList(), targetTypes = emptyMap())
  var lastTargetType: String? = null
  var lastTargetName: String? = null
  var manuallyWrittenAttributeName: String? = null
  val manuallyWrittenValue = StringBuilder()
  val result = ArrayList<ManuallyWrittenAttribute>()
  val targetTypes = HashMap<String, String>()
  fun registerManuallyWrittenAttribute() {
    if (manuallyWrittenAttributeName != null) {
      result.add(
        ManuallyWrittenAttribute(
          targetName = lastTargetName ?: error("manually written attribute $manuallyWrittenAttributeName in a target without name in $bazelFile"),
          targetType = lastTargetType ?: error("manually written attribute $manuallyWrittenAttributeName outside a target in $bazelFile"),
          attributeName = manuallyWrittenAttributeName!!,
          attributeValue = manuallyWrittenValue.toString().removeSuffix(",")
        )
      )
      manuallyWrittenAttributeName = null
      manuallyWrittenValue.clear()
    }
  }

  for (line in lines) {
    val functionCallMatch = FUNCTION_CALL_REGEX.matchEntire(line)
    if (functionCallMatch != null) {
      lastTargetType = functionCallMatch.groupValues[1]
      lastTargetName = null
      continue
    }
    val attributeMatch = ATTRIBUTE_ASSIGNMENT_REGEX.matchEntire(line)
    if (attributeMatch != null) {
      val name = attributeMatch.groupValues[1]
      val valueStart = attributeMatch.groupValues[2]
      if (name == "name") {
        val targetName = valueStart.removeSurrounding("\"", "\",")
        lastTargetName = targetName
        lastTargetType?.let { targetTypes.putIfAbsent(targetName, it) }
        continue
      }
      registerManuallyWrittenAttribute()
      val targetType = lastTargetType
      if (targetType != null && MANUALLY_WRITTEN_ATTRIBUTES.any { it.matches(ruleName = targetType, attributeName = name) }) {
        manuallyWrittenAttributeName = name
        manuallyWrittenValue.clear()
        manuallyWrittenValue.append(valueStart)
      }
      continue
    }
    else if (line.startsWith(")")) { // since Bazel files are formatted by Buildifier, it guarantees that this indicates an end of a multi-line call
      registerManuallyWrittenAttribute()
    }
    if (manuallyWrittenAttributeName != null) {
      manuallyWrittenValue.append('\n').append(line)
    }
  }
  return ParsedBuildFile(attributes = result, targetTypes = targetTypes)
}

private class ManuallyWrittenAttributeValue(val value: String): RenderableAttributeWithComment {
  override val comment: String = "manually written attribute"
  override fun render(): String = value
}
