// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import java.nio.file.Path

internal object KotlinNewApiIconClassSpecificsGenerator : IconClassSpecificsGenerator {
  override val classAnnotation: String = "@ApiStatus.Experimental"

  private fun getKotlinClassName(iconClassInfo: IconClassInfo): String =
    iconClassInfo.kotlinClassName ?: iconClassInfo.className.replace("Icons", "IconDescriptors")

  override fun generateIconClassName(iconClassInfo: IconClassInfo): String = getKotlinClassName(iconClassInfo)

  override fun pickOutFile(iconClassInfo: IconClassInfo): Path {
    val file = iconClassInfo.kotlinOutFile ?: error("kotlinOutFile is not set for '${iconClassInfo.className}'")
    return file.resolveSibling("${generateIconClassName(iconClassInfo)}.kt")
  }

  override fun pickPackageName(iconClassInfo: IconClassInfo): String? = iconClassInfo.kotlinPackageName

  override fun shouldClassBeFinal(iconClassInfo: IconClassInfo): Boolean = true

  override fun appendCustomComments(generator: IconsClassGenerator, result: StringBuilder) {
    generator.append(result, " * Using these descriptors requires the IconManager to be initialized.", 0)
  }

  override fun appendCustomImports(generator: IconsClassGenerator, result: StringBuilder) {
    generator.append(result, "import org.jetbrains.annotations.ApiStatus", 0)
  }

  override fun appendTopLevelStatements(generator: IconsClassGenerator, result: StringBuilder, info: IconClassInfo, images: Collection<ImageInfo>) {
    // No top-level statements needed. The new API needs no `load` helper.
  }

  override fun appendPackageStatement(generator: IconsClassGenerator, result: StringBuilder, packageName: String?) {
    generator.append(result, "package $packageName\n", 0)
  }

  override fun appendScheduledForRemovalImport(generator: IconsClassGenerator, result: StringBuilder) {
    // Kotlin uses `@ApiStatus.ScheduledForRemoval`, and `ApiStatus` is always imported.
  }

  override fun appendTopLevelTypeDeclaration(generator: IconsClassGenerator, result: StringBuilder, info: IconClassInfo, realClassName: String) {
    result.append("object ").append(realClassName).append(" {\n")
  }

  override fun appendNestedTypeDeclaration(generator: IconsClassGenerator, result: StringBuilder, className: CharSequence, level: Int) {
    generator.append(result, "object $className {", level)
  }

  // Mirrors the Java `@Deprecated` and `@ScheduledForRemoval` of the legacy class, with the deprecation comment as the message.
  override fun appendMemberAnnotations(generator: IconsClassGenerator, result: StringBuilder, image: ImageInfo, level: Int) {
    val deprecation = image.deprecation ?: return
    // if first in block, do not add yet another extra newline
    if (result[result.length - 1] != '\n' || result[result.length - 2] != '\n') {
      result.append('\n')
    }
    generator.append(result, deprecatedAnnotation(deprecation.comment, deprecation.replacementReference), level)
    if (image.scheduledForRemoval) {
      generator.append(result, "@ApiStatus.ScheduledForRemoval", level)
    }
  }

  override fun appendIconProperty(generator: IconsClassGenerator, result: StringBuilder, topLevelClass: String, javaDoc: String, image: ImageInfo, iconName: CharSequence, key: Int, mappings: Map<String, String>?, level: Int) {
    val imagePathCodeParameter = image.sourceCodeParameterName
    val path = mappings?.get(imagePathCodeParameter) ?: imagePathCodeParameter
    generator.append(
      result, "${javaDoc}@JvmStatic val $iconName: IconDescriptor = imageResIconDescriptor(\"$path\", $topLevelClass::class.java.classLoader)", level
    )
  }

  override fun appendDeprecatedIconPropertyMapping(generator: IconsClassGenerator, result: StringBuilder, javaDoc: String, oldName: String, iconName: CharSequence, level: Int) {
    generator.append(result, "${javaDoc}${deprecatedAnnotation("Use $iconName", iconName.toString())} @JvmStatic val $oldName: IconDescriptor = $iconName", level)
  }

  override fun appendDeprecationReplacementClass(generator: IconsClassGenerator, result: StringBuilder, iconName: CharSequence, deprecation: DeprecationData, level: Int) {
    // the Java class loads the replacement relative to the context class; a class loader resolves it from the root
    val path = deprecation.replacement!!.removePrefix("/")
    generator.append(
      result, "@JvmStatic val $iconName: IconDescriptor = imageResIconDescriptor(\"$path\", ${deprecation.replacementContextClazz}::class.java.classLoader)", level
    )
  }

  override fun appendDeprecationReplacementReferenceClass(generator: IconsClassGenerator, result: StringBuilder, iconName: CharSequence, deprecation: DeprecationData, level: Int) {
    generator.append(result, "@JvmStatic val $iconName: IconDescriptor = ${deprecation.replacementReference}", level)
  }

  private fun deprecatedAnnotation(comment: String?, replacementReference: String?): String {
    // `{@link #X}` is Javadoc syntax, a Kotlin message names the field plainly
    val message = (comment ?: "Deprecated icon").replace(Regex("""\{@link #([^}]+)}"""), "$1")
    val replaceWith = if (replacementReference == null) "" else ", ReplaceWith(${kotlinString(replacementReference)})"
    return "@Deprecated(${kotlinString(message)}$replaceWith)"
  }

  private fun kotlinString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
}