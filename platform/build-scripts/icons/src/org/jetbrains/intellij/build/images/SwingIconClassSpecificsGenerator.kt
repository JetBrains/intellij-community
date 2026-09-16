// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import java.nio.file.Path

private const val ICON_MANAGER_CODE = "IconManager.getInstance()"

internal object SwingIconClassSpecificsGenerator : IconClassSpecificsGenerator {
  override val classAnnotation: String? = null

  override fun generateIconClassName(iconClassInfo: IconClassInfo): String = iconClassInfo.className

  override fun pickOutFile(iconClassInfo: IconClassInfo): Path = iconClassInfo.outFile

  override fun pickPackageName(iconClassInfo: IconClassInfo): String = iconClassInfo.packageName

  override fun shouldClassBeFinal(iconClassInfo: IconClassInfo): Boolean = iconClassInfo.className != "AllIcons"

  override fun appendCustomImports(generator: IconsClassGenerator, result: StringBuilder) {
    generator.append(result, "import com.intellij.ui.IconManager;", 0)
    generator.append(result, "import org.jetbrains.annotations.NotNull;", 0)
    result.append('\n')
    generator.append(result, "import javax.swing.*;", 0)
  }

  override fun appendTopLevelStatements(generator: IconsClassGenerator, result: StringBuilder, info: IconClassInfo, images: Collection<ImageInfo>) {
    if (info.mappings.isNullOrEmpty() || info.images.find { !info.mappings.containsKey(it.sourceCodeParameterName) } != null) {
      generator.append(result, "private static @NotNull Icon load(@NotNull String path, int cacheKey, int flags) {", 1)
      generator.append(result, "return ${ICON_MANAGER_CODE}.loadRasterizedIcon(path, ${info.className}.class.getClassLoader(), cacheKey, flags);", 2)
      generator.append(result, "}", 1)
    }

    if (!info.mappings.isNullOrEmpty() && info.images.find { info.mappings.containsKey(it.sourceCodeParameterName) } != null) {
      generator.append(result, "private static @NotNull Icon load(@NotNull String expUIPath, @NotNull String path, int cacheKey, int flags) {", 1)
      generator.append(result, "return ${ICON_MANAGER_CODE}.loadRasterizedIcon(path, expUIPath, ${info.className}.class.getClassLoader(), cacheKey, flags);", 2)
      generator.append(result, "}", 1)
    }

    val customExternalLoad = images.any { it.deprecation?.replacementContextClazz != null }
    if (customExternalLoad) {
      result.append('\n')
      generator.append(result, "private static @NotNull Icon load(@NotNull String path, @NotNull Class<?> clazz) {", 1)
      generator.append(result, "return ${ICON_MANAGER_CODE}.getIcon(path, clazz);", 2)
      generator.append(result, "}", 1)
    }
  }

  override fun appendDeprecatedIconPropertyMapping(generator: IconsClassGenerator, result: StringBuilder, javaDoc: String, oldName: String, iconName: CharSequence, level: Int) {
    generator.append(result, "${javaDoc}public static final @Deprecated @NotNull Icon $oldName = $iconName;", level)
  }

  override fun appendIconProperty(generator: IconsClassGenerator, result: StringBuilder, topLevelClass: String, javaDoc: String, image: ImageInfo, iconName: CharSequence, key: Int, mappings: Map<String, String>?, level: Int) {
    val imagePathCodeParameter = image.sourceCodeParameterName
    generator.append(
      result, "${javaDoc}public static final @NotNull Icon $iconName = " +
              "load(${generator.appendExpUIPath(imagePathCodeParameter, mappings)}\"$imagePathCodeParameter\", $key, ${image.getFlags()});", level
    )
  }

  override fun appendDeprecationReplacementClass(generator: IconsClassGenerator, result: StringBuilder, iconName: CharSequence, deprecation: DeprecationData, level: Int) {
    generator.append(
      result, "public static final @NotNull Icon $iconName = " +
              "load(\"${deprecation.replacement}\", ${deprecation.replacementContextClazz}.class);", level
    )
  }

  override fun appendDeprecationReplacementReferenceClass(generator: IconsClassGenerator, result: StringBuilder, iconName: CharSequence, deprecation: DeprecationData, level: Int) {
    generator.append(result, "public static final @NotNull Icon $iconName = ${deprecation.replacementReference};", level)
  }
}