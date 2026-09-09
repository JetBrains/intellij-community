// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import java.nio.file.Path

internal interface IconClassSpecificsGenerator {
  val classAnnotation: String?
  fun generateIconClassName(iconClassInfo: IconClassInfo): String
  fun pickOutFile(iconClassInfo: IconClassInfo): Path
  fun pickPackageName(iconClassInfo: IconClassInfo): String?
  fun shouldClassBeFinal(iconClassInfo: IconClassInfo): Boolean
  fun appendTopLevelStatements(generator: IconsClassGenerator, result: StringBuilder, info: IconClassInfo, images: Collection<ImageInfo>)
  fun appendCustomImports(generator: IconsClassGenerator, result: StringBuilder)
  fun appendDeprecatedIconPropertyMapping(generator: IconsClassGenerator, result: StringBuilder, javaDoc: String, oldName: String, iconName: CharSequence, level: Int)
  fun appendIconProperty(generator: IconsClassGenerator, result: StringBuilder, topLevelClass: String, javaDoc: String, image: ImageInfo, iconName: CharSequence, key: Int, mappings: Map<String, String>?, level: Int)
  fun appendDeprecationReplacementClass(generator: IconsClassGenerator, result: StringBuilder, iconName: CharSequence, deprecation: DeprecationData, level: Int)
  fun appendDeprecationReplacementReferenceClass(generator: IconsClassGenerator, result: StringBuilder, iconName: CharSequence, deprecation: DeprecationData, level: Int)

  // Whether to emit the given image. Java generators keep every image.
  // A Kotlin generator drops deprecated icons.
  fun includeImage(image: ImageInfo): Boolean = true

  // The default methods below emit Java syntax. A Kotlin generator overrides them.
  fun appendPackageStatement(generator: IconsClassGenerator, result: StringBuilder, packageName: String?) {
    generator.append(result, "package $packageName;\n", 0)
  }

  fun appendScheduledForRemovalImport(generator: IconsClassGenerator, result: StringBuilder) {
    generator.append(result, "import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;", 0)
    result.append('\n')
  }

  fun appendTopLevelTypeDeclaration(generator: IconsClassGenerator, result: StringBuilder, info: IconClassInfo, realClassName: String) {
    result.append("public")
    // backward compatibility
    if (shouldClassBeFinal(info)) {
      result.append(" final")
    }
    result.append(" class ").append(realClassName).append(" {\n")
  }

  fun appendNestedTypeDeclaration(generator: IconsClassGenerator, result: StringBuilder, className: CharSequence, level: Int) {
    val annotation = classAnnotation
    if (!annotation.isNullOrBlank()) {
      generator.append(result, annotation, level)
    }
    generator.append(result, "public static final class $className {", level)
  }

  fun appendMemberAnnotations(generator: IconsClassGenerator, result: StringBuilder, image: ImageInfo, level: Int) {
    if (image.used || image.deprecated) {
      val deprecationComment = image.deprecation?.comment
      if (deprecationComment != null) {
        // if first in block, do not add yet another extra newline
        if (result[result.length - 1] != '\n' || result[result.length - 2] != '\n') {
          result.append('\n')
        }
        generator.append(result, "/** @deprecated $deprecationComment */", level)
      }
      generator.append(result, "@SuppressWarnings(\"unused\")", level)
    }
    if (image.deprecated) {
      generator.append(result, "@Deprecated", level)
    }
    if (image.scheduledForRemoval) {
      generator.append(result, "@ScheduledForRemoval", level)
    }
  }

  fun appendCustomComments(generator: IconsClassGenerator, result: StringBuilder) {
    // No custom comments by default
  }
}