// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import java.nio.file.Path

internal object JewelIconClassSpecificsGenerator : IconClassSpecificsGenerator {
  override val classAnnotation: String= "@GeneratedFromIntelliJSources"
  private const val METALAVA_DEPRECATION_SUPPRESSION: String= "@SuppressWarnings(\"DeprecationMismatch\")"

  override fun generateIconClassName(iconClassInfo: IconClassInfo): String = "${iconClassInfo.className}Keys"

  override fun pickOutFile(iconClassInfo: IconClassInfo): Path = iconClassInfo.jewelOutFile

  override fun pickPackageName(iconClassInfo: IconClassInfo): String = iconClassInfo.jewelPackageName ?: iconClassInfo.packageName

  override fun shouldClassBeFinal(iconClassInfo: IconClassInfo): Boolean = true

  override fun appendCustomImports(generator: IconsClassGenerator, result: StringBuilder) {
    generator.append(result, "import org.jetbrains.annotations.NotNull;", 0)
    result.append('\n')
    generator.append(result, "import org.jetbrains.jewel.foundation.GeneratedFromIntelliJSources;", 0)
    result.append('\n')
    generator.append(result, "import org.jetbrains.jewel.ui.icon.IntelliJIconKey;", 0)
  }

  override fun appendTopLevelStatements(generator: IconsClassGenerator, result: StringBuilder, info: IconClassInfo, images: Collection<ImageInfo>) {
    // No top-level statements needed
  }

  override fun appendDeprecatedIconPropertyMapping(generator: IconsClassGenerator, result: StringBuilder, javaDoc: String, oldName: String, iconName: CharSequence, level: Int) {
    generator.append(result, "${javaDoc}\n$METALAVA_DEPRECATION_SUPPRESSION\n$classAnnotation\n" +
                             "public static final @Deprecated @NotNull IntelliJIconKey $oldName = $iconName;", level)
  }

  override fun appendIconProperty(generator: IconsClassGenerator, result: StringBuilder, topLevelClass: String, javaDoc: String, image: ImageInfo, iconName: CharSequence, key: Int, mappings: Map<String, String>?, level: Int) {
    val imagePathCodeParameter = image.sourceCodeParameterName
    val expUiPath = mappings?.let { it[imagePathCodeParameter] } ?: imagePathCodeParameter
    generator.append(
      result, "${javaDoc}\n$classAnnotation\npublic static final @NotNull IntelliJIconKey $iconName = " +
              "new IntelliJIconKey(\"$imagePathCodeParameter\", \"${expUiPath}\", $topLevelClass.class);", level
    )
  }

  override fun appendDeprecationReplacementClass(generator: IconsClassGenerator, result: StringBuilder, iconName: CharSequence, deprecation: DeprecationData, level: Int) {
    generator.append(
      result, "$classAnnotation\npublic static final @NotNull IntelliJIconKey $iconName = " +
              "new IntelliJIconKey(\"${deprecation.replacement}\", \"${deprecation.replacement}\", ${deprecation.replacementContextClazz}.class);", level
    )
  }

  override fun appendDeprecationReplacementReferenceClass(generator: IconsClassGenerator, result: StringBuilder, iconName: CharSequence, deprecation: DeprecationData, level: Int) {
    generator.append(result, "$classAnnotation\npublic static final @NotNull IntelliJIconKey $iconName = ${deprecation.replacementReference};", level)
  }
}