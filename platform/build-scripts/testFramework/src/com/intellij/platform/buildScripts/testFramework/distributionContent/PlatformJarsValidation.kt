// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.DevDistProductReport
import com.intellij.platform.distributionContent.DistFileRow
import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ProductReviewReport
import com.intellij.platform.distributionContent.writeDevDistProductReport
import com.intellij.platform.distributionContent.writeProductReviewReport
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.devDist.PlatformJarRows
import org.jetbrains.intellij.build.devDist.derivePlatformJars
import org.jetbrains.intellij.build.devDist.deriveProductReviewReport
import org.jetbrains.intellij.build.devDist.devDistProductToken
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Compares the source derivation with the build layout, then checks the independently packed platform jars.
 *
 * At the CONTENT stage, a compact report records product choices and distribution files. An artifact records expanded observations.
 * Each product owns one YAML file at [PackagingTargetSpec.contentYamlPath]. These reports are not build inputs.
 */
@Internal
fun createPlatformJarsValidations(targetId: String): List<PackagingTargetValidationSpec> = listOf(
  PackagingTargetValidationSpec(
    targetId = targetId,
    name = "platform-jars",
    problemMessage = "the source derivation differs from the product's platform layout",
    stage = PackagingTargetValidationStage.LAYOUT,
    validator = { context -> validateSourcePlatformJars(context) },
  ),
  PackagingTargetValidationSpec(
    targetId = targetId,
    name = "platform-jars-packed",
    problemMessage = "the build packed different platform jars than the platform layout states",
    validator = { context -> validatePackedPlatformJars(context) },
  ),
  PackagingTargetValidationSpec(
    targetId = targetId,
    name = "product-report",
    problemMessage = "the product report differs from the packaged distribution",
    validator = { context -> validateProductReport(context) },
  ),
)

/** The directory of the platform jars in a platform report entry name. A product module report entry names the same prefix. */
private const val PLATFORM_LIB_PREFIX = "dist.all/lib/"

/** The name a product module report gives a jar it packs under the product's own jar name. */
private const val HIDDEN_FILE_NAME = "<file>"

private const val PRODUCT_REPORT_REPAIR =
  "The product report differs from this build's product choices or distribution files. " +
  "The patch updates only this product's YAML file. Apply the patch or accept it in the Diff Viewer, then run this test again."

/** The version in a library file name: the match of `-1.2.3.jar` in `lib/foo-1.2.3.jar`, or of `/1.2.3/` in a path. */
private val libraryVersionRegex = Regex("([/-])(\\d+)(\\.\\d+){1,2}[0-9a-zA-Z\\-_.]*(/|.jar)")

private fun derivePlatform(context: PackagingTargetValidationContext): PlatformJarRows {
  val buildContext = context.layout.buildContext
  val product = devDistProductToken(projectHome = context.projectHome, productProperties = buildContext.productProperties)
  return derivePlatformJars(
    product = product,
    layout = context.layout.distributionState.platformLayout,
    findModule = buildContext.outputProvider::findRequiredModule,
  )
}

private fun validateSourcePlatformJars(context: PackagingTargetValidationContext): List<PackagingCheckFailure> {
  val productProperties = context.target.createProductProperties(context.projectHome)
  val product = devDistProductToken(projectHome = context.projectHome, productProperties = productProperties)
  val source = derivePlatformJars(product = product, productProperties = productProperties, outputProvider = context.outputProvider)
  val actual = derivePlatform(context)
  return listOfNotNull(
    comparePlatformRows("jars", source.jars, actual.jars),
    comparePlatformRows("libraries", source.libraries, actual.libraries),
    comparePlatformRows("merged libraries", source.mergedLibraries, actual.mergedLibraries),
    comparePlatformRows("content modules", source.contentModules, actual.contentModules),
  )
}

private fun <T> comparePlatformRows(section: String, source: List<T>, actual: List<T>): PackagingCheckFailure? {
  val sourceRows = source.toSet()
  val actualRows = actual.toSet()
  if (sourceRows == actualRows) {
    return null
  }
  return PackagingCheckFailure(
    name = "platform-jars-source: $section",
    error = AssertionError(
      "The source derivation differs from the build layout for $section.\n" +
      "Only in the source: ${(sourceRows - actualRows).map { it.toString() }.sorted().joinToString()}\n" +
      "Only in the build layout: ${(actualRows - sourceRows).map { it.toString() }.sorted().joinToString()}",
    ),
  )
}

private fun validatePackedPlatformJars(context: PackagingTargetValidationContext): List<PackagingCheckFailure> {
  val derived = derivePlatform(context).jars.associate { it.relativeOutputFile to it.members.toSet() }
  val content = context.content()
  val packed = LinkedHashMap<String, LinkedHashSet<String>>()
  val hidden = HashSet<String>()
  fun record(entry: FileEntry) {
    if (entry.modules.isEmpty() && entry.contentModules.isEmpty()) {
      return
    }
    val members = when {
      entry.name == HIDDEN_FILE_NAME -> hidden
      !entry.name.startsWith(PLATFORM_LIB_PREFIX) -> return
      else -> packed.computeIfAbsent(entry.name.removePrefix(PLATFORM_LIB_PREFIX)) { LinkedHashSet() }
    }
    entry.modules.mapTo(members) { it.name }
    entry.contentModules.mapTo(members) { it.name }
  }
  content.platform.forEach(::record)
  for (report in content.productModules) {
    report.content.forEach(::record)
  }

  val failures = ArrayList<PackagingCheckFailure>()
  for ((path, members) in derived.toSortedMap()) {
    val derivedMembers = members - hidden
    val packedMembers = packed[path]
    if (packedMembers == null) {
      if (derivedMembers.isNotEmpty()) {
        failures.add(packedPlatformJarFailure(path, "the layout states the jar and the build packed no jar at the path, members ${describe(derivedMembers)}"))
      }
    }
    else if (packedMembers != derivedMembers) {
      failures.add(packedPlatformJarFailure(
        path,
        "members only in the layout: ${describe(derivedMembers - packedMembers)}; members only packed: ${describe(packedMembers - derivedMembers)}",
      ))
    }
  }
  for ((path, members) in packed.toSortedMap()) {
    if (path !in derived) {
      failures.add(packedPlatformJarFailure(path, "the build packed the jar and the layout states no jar at the path, members ${describe(members)}"))
    }
  }
  return failures
}

private fun packedPlatformJarFailure(path: String, text: String): PackagingCheckFailure {
  return PackagingCheckFailure(
    name = "platform-jars-packed: $path",
    error = AssertionError(
      "Platform jar `lib/$path`: $text.\n" +
      "The layout is `PlatformLayout.includedModules`, read through `derivePlatformJars`, and the packed side is the" +
      " content report of this build. The two share the layout, so the difference is in the packer or in the derivation."
    ),
  )
}

private fun describe(members: Collection<String>): String = "[${members.sorted().joinToString()}]"

private fun validateProductReport(context: PackagingTargetValidationContext): List<PackagingCheckFailure> {
  val buildContext = context.layout.buildContext
  val content = context.content()
  val distFiles = buildContext.getDistFiles(os = null, arch = null, libcImpl = null).map {
    DistFileRow(
      os = it.os?.osId,
      arch = it.arch?.dirName,
      libc = it.libcImpl?.toString(),
      path = removeVersionFromPath(it.relativePath),
    )
  }
  writeProductObservation(
    file = context.productObservationFile,
    report = DevDistProductReport(
      bundledPlugins = content.bundled,
      nonBundledPlugins = content.nonBundled,
      platformContentModules = derivePlatform(context).contentModules.map { it.module },
      distFiles = distFiles,
    ),
  )
  return validateProductReport(
    projectHome = context.projectHome,
    contentYamlPath = context.target.contentYamlPath,
    report = deriveProductReviewReport(
      productProperties = buildContext.productProperties,
      bundledPlugins = content.bundled,
      nonBundledPlugins = content.nonBundled,
      distFiles = distFiles,
      platformEntries = content.platform,
    ),
  )
}

internal fun writeProductObservation(file: Path, report: DevDistProductReport) {
  val text = writeDevDistProductReport(report)
  file.parent.createDirectories()
  file.writeText(text)
  println("Expanded product observation: $file")
}

internal fun validateProductReport(
  projectHome: Path,
  contentYamlPath: String,
  report: ProductReviewReport,
): List<PackagingCheckFailure> {
  val relativePath = Path.of(contentYamlPath)
  require(contentYamlPath.isNotBlank() && !relativePath.isAbsolute && relativePath.none { it.toString() == ".." } &&
          relativePath.normalize() == relativePath) {
    "The product report path must be relative to the project root: $contentYamlPath."
  }
  val file = projectHome.resolve(relativePath)
  val currentText = if (file.exists()) file.readText() else ""
  val desiredText = writeProductReviewReport(report)
  if (desiredText == currentText) {
    return emptyList()
  }
  return listOf(
    createFilePatchFailure(
      name = "product-report-out-of-date",
      file = file,
      projectHome = projectHome,
      currentText = currentText,
      desiredText = desiredText,
      context = PRODUCT_REPORT_REPAIR,
    )
  )
}

/** Strips the version of a library file name from [path]: `lib/foo-1.2.3.jar` becomes `lib/foo-1.jar`. */
private fun removeVersionFromPath(path: String): String {
  return libraryVersionRegex.replace(path) { match ->
    val groups = match.groups
    groups[1]!!.value + groups[2]!!.value + groups.last()!!.value
  }
}
