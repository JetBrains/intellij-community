// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.plugins

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.PluginLayout
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.Predicate
import kotlin.io.path.useLines

/**
 * Predicate to test if the given plugin should be published to plugins.jetbrains.com
 *
 * @see `build/plugins-autoupload.txt` for the specification
 * @see [org.jetbrains.intellij.build.ProductModulesLayout.buildAllCompatiblePlugins]
 * @see [org.jetbrains.intellij.build.ProductModulesLayout.pluginModulesToPublish]
 */
@ApiStatus.Internal
class PluginAutoPublishList(private val context: BuildContext) : Predicate<PluginLayout> {
  private val expectedFile: Path = context.paths.communityHomeDir.resolve("../build/plugins-autoupload.txt")

  private val file: Path? by lazy {
    when {
      Files.isRegularFile(expectedFile) -> expectedFile
      // public sources build
      context.paths.projectHome.toUri() == context.paths.communityHomeDir.toUri() -> null
      else -> error("File '$expectedFile' must exist")
    }
  }

  val config: Collection<String> by lazy {
    file?.useLines { lines ->
      lines
        .map { StringUtil.split(it, "//", true, false)[0] }
        .map { StringUtil.split(it, "#", true, false)[0] }
        .map { it.trim() }.filter { !it.isEmpty() }
        .toCollection(TreeSet(String.CASE_INSENSITIVE_ORDER))
    } ?: emptyList()
  }

  private val predicate: Predicate<PluginLayout> = Predicate<PluginLayout> { plugin ->
    if (file == null) return@Predicate false
    val productCode = context.applicationInfo.productCode
    val mainModuleName = plugin.mainModule

    val includeInAllProducts = config.contains(mainModuleName)
    val includeInProduct = config.contains("+$productCode:$mainModuleName")
    val excludedFromProduct = config.contains("-$productCode:$mainModuleName") || config.contains("-$productCode:*")

    if (includeInProduct && (excludedFromProduct || includeInAllProducts)) {
      context.messages.error("Unsupported rules combination: " + config.filter {
        it == mainModuleName || it.endsWith(":$mainModuleName")
      })
    }

    !excludedFromProduct && (includeInAllProducts || includeInProduct)
  }

  override fun test(pluginLayout: PluginLayout): Boolean {
    return predicate.test(pluginLayout)
  }

  override fun toString(): String {
    return "$expectedFile"
  }
}