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

  // Drop deprecated icons. The descriptor class holds only current icons.
  override fun includeImage(image: ImageInfo): Boolean = !image.deprecated

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
    // Kotlin uses a fully-qualified annotation. No import is needed.
  }

  override fun appendTopLevelTypeDeclaration(generator: IconsClassGenerator, result: StringBuilder, info: IconClassInfo, realClassName: String) {
    result.append("object ").append(realClassName).append(" {\n")
  }

  override fun appendNestedTypeDeclaration(generator: IconsClassGenerator, result: StringBuilder, className: CharSequence, level: Int) {
    generator.append(result, "object $className {", level)
  }

  override fun appendMemberAnnotations(generator: IconsClassGenerator, result: StringBuilder, image: ImageInfo, level: Int) {
    // The descriptor class carries no annotations.
  }

  override fun appendIconProperty(generator: IconsClassGenerator, result: StringBuilder, topLevelClass: String, javaDoc: String, image: ImageInfo, iconName: CharSequence, key: Int, mappings: Map<String, String>?, level: Int) {
    val imagePathCodeParameter = image.sourceCodeParameterName
    val path = mappings?.get(imagePathCodeParameter) ?: imagePathCodeParameter
    generator.append(
      result, "${javaDoc}@JvmStatic val $iconName: IconDescriptor = imageIconDescriptor(\"$path\", $topLevelClass::class.java.classLoader)", level
    )
  }

  override fun appendDeprecatedIconPropertyMapping(generator: IconsClassGenerator, result: StringBuilder, javaDoc: String, oldName: String, iconName: CharSequence, level: Int) {
    // The descriptor class holds no deprecated icon aliases.
  }

  override fun appendDeprecationReplacementClass(generator: IconsClassGenerator, result: StringBuilder, iconName: CharSequence, deprecation: DeprecationData, level: Int) {
    // Deprecated icons are dropped, so this is never called.
  }

  override fun appendDeprecationReplacementReferenceClass(generator: IconsClassGenerator, result: StringBuilder, iconName: CharSequence, deprecation: DeprecationData, level: Int) {
    // Deprecated icons are dropped, so this is never called.
  }
}