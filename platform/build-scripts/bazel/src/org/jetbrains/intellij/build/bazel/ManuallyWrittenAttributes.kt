// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Reads and keeps the manually written attributes and the rule names of the targets of a BUILD file. */
internal class ManuallyWrittenAttributes(private val bazelFilesLoader: BazelFilesLoader) {
  private val parsedBuildFiles = ConcurrentHashMap<Path, ParsedBuildFile>()

  fun getManuallyWrittenAttributes(module: ModuleDescriptor, attributeName: String): List<ManuallyWrittenAttribute> {
    return getParsedBuildFile(module).attributes.filter { it.targetName == module.targetName && it.attributeName == attributeName }
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

private class ParsedBuildFile(
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

internal const val DATA_ATTRIBUTE = "data"
internal const val NON_CLASSPATH_DATA_ATTRIBUTE = "non_classpath_data"
private val MANUALLY_WRITTEN_ATTRIBUTES = setOf(DATA_ATTRIBUTE, NON_CLASSPATH_DATA_ATTRIBUTE)

private val FUNCTION_CALL_REGEX = Regex("""([a-zA-Z_][a-zA-Z0-9_]*)\(""")
private val ATTRIBUTE_ASSIGNMENT_REGEX = Regex(""" *([a-zA-Z_][a-zA-Z0-9_]*) = (.*)""")

private fun parseBuildFile(bazelFile: Path, fileContent: String?): ParsedBuildFile {
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
      if (name in MANUALLY_WRITTEN_ATTRIBUTES) {
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
