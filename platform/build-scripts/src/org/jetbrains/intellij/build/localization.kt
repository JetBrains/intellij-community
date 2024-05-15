// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build

import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*

internal fun getLocalizationDir(context: BuildContext): Path? {
  val localizationDir = context.paths.communityHomeDir.parent.resolve("localization")
  if (Files.notExists(localizationDir)) {
    Span.current().addEvent("unable to find 'localization' directory, skip localization bundling")
    return null
  }
  return localizationDir
}

internal suspend fun localizeModules(moduleNames: Collection<String>, context: BuildContext) {
  if (context.isStepSkipped(BuildOptions.LOCALIZE_STEP)) {
    return
  }

  val localizationDir = getLocalizationDir(context) ?: return

  val jarPackagerDependencyHelper = (context as BuildContextImpl).jarPackagerDependencyHelper
  val modules = moduleNames.asSequence()
    .mapNotNull { context.findModule(it) }
    .flatMap { m ->
      jarPackagerDependencyHelper.readPluginIncompleteContentFromDescriptor(m).mapNotNull { context.findModule(it) } + sequenceOf(m)
    }
    .flatMap { m ->
      m.dependenciesList.dependencies.asSequence().filterIsInstance<JpsModuleDependency>().mapNotNull { it.module } + sequenceOf(m)
    }
    .distinctBy { m -> m.name }
    .toList()

  TraceManager.spanBuilder("bundle localizations").setAttribute("moduleCount", modules.size.toLong()).useWithScope {
    for (module in modules) {
      launch {
        val resourceRoots = module.getSourceRoots(JavaResourceRootType.RESOURCE).toList()
        if (resourceRoots.isEmpty()) {
          return@launch
        }

        withContext(Dispatchers.IO) {
          TraceManager.spanBuilder("bundle localization").setAttribute("module", module.name).use {
            buildInBundlePropertiesLocalization(
              module = module,
              bundlePropertiesLocalization = localizationDir.resolve("properties"),
              resourceRoots = resourceRoots,
              context = context,
            )
            buildInInspectionsIntentionsLocalization(
              module = module,
              context = context,
              inspectionsIntentionsLocalization = localizationDir.resolve("inspections_intentions"),
              resourceRoots = resourceRoots,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalPathApi::class)
private fun buildInInspectionsIntentionsLocalization(
  module: JpsModule,
  context: BuildContext,
  inspectionsIntentionsLocalization: Path,
  resourceRoots: List<JpsTypedModuleSourceRoot<JavaResourceRootProperties>>,
) {
  for (resourceRoot in resourceRoots) {
    val isInspectionIntentionsPresentInModule = sequenceOf("fileTemplates", "intentionDescriptions", "inspectionDescriptions", "postfixTemplates")
      .map { resourceRoot.path.resolve(it) }
      .any { Files.exists(it) }
    if (!isInspectionIntentionsPresentInModule) {
      return
    }

    inspectionsIntentionsLocalization.walk(PathWalkOption.INCLUDE_DIRECTORIES)
      .filter { Files.isDirectory(it) && it.name == module.name }
      .forEach { moduleLocalizationSources ->
        val sourcesLang = inspectionsIntentionsLocalization.relativize(moduleLocalizationSources).getName(0)
        val moduleTargetLangLocalizationDir = resourceRoot.path.relativize(resourceRoot.path.resolve("localization").resolve(sourcesLang))

        moduleLocalizationSources.walk().filter { Files.isRegularFile(it) }.forEach { localizationFileSourceByLangAndModule ->
          // e.g.
          // localization/inspections_intentions/ja/fleet.plugins.kotlin.backend/inspectionDescriptions/NewEntityRequiredProperties.html
          // ->
          // out/classes/production/fleet.plugins.kotlin.backend/localization/ja/inspectionDescriptions/NewEntityRequiredProperties.html

          val localizationFileTargetRelativePath = moduleTargetLangLocalizationDir.resolve(
            moduleLocalizationSources.relativize(localizationFileSourceByLangAndModule)
          )
          val localizationFileTargetAbsolutePath = context.getModuleOutputDir(module).resolve(localizationFileTargetRelativePath)

          Files.createDirectories(localizationFileTargetAbsolutePath.parent)
          Files.copy(localizationFileSourceByLangAndModule, localizationFileTargetAbsolutePath, StandardCopyOption.REPLACE_EXISTING)
        }
      }
  }
}

private fun buildInBundlePropertiesLocalization(
  module: JpsModule,
  bundlePropertiesLocalization: Path,
  resourceRoots: List<JpsTypedModuleSourceRoot<JavaResourceRootProperties>>,
  context: BuildContext,
) {
  val knownBundlePropertiesList = HashMap<Path, Path>()
  for (resourceRoot in resourceRoots) {
    if (!Files.isDirectory(resourceRoot.path)) {
      continue
    }

    Files.walkFileTree(resourceRoot.path, object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        val dirName = dir.fileName.toString()
        if (dirName == "fileTemplates" || dirName == "META-INF" || dirName == "inspections" || dirName == "icons") {
          return FileVisitResult.SKIP_SUBTREE
        }
        return FileVisitResult.CONTINUE
      }

      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        val fileName = file.fileName
        if (fileName.toString().endsWith("Bundle.properties")) {
          knownBundlePropertiesList.put(fileName, resourceRoot.path.relativize(file))
        }
        return FileVisitResult.CONTINUE
      }
    })
  }

  if (knownBundlePropertiesList.isEmpty()) {
    return
  }

  Files.walkFileTree(bundlePropertiesLocalization, object : SimpleFileVisitor<Path>() {
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val origRelativePath = knownBundlePropertiesList.get(file.fileName) ?: return FileVisitResult.CONTINUE

      // e.g.
      // localization/properties/ja/PersonBundle.properties
      // ->
      // out/classes/production/intellij.ae.personalization.main/messages/PersonBundle_ja.properties

      val localizedRelative = bundlePropertiesLocalization.relativize(file)
      val lang = localizedRelative.getName(0)

      val targetNameWithLangSuffix = origRelativePath.nameWithoutExtension + "_$lang." + origRelativePath.extension
      val localizedBundleDstPath = context.getModuleOutputDir(module).resolve(origRelativePath.parent.resolve(targetNameWithLangSuffix))
      Files.createDirectories(localizedBundleDstPath.parent)
      Files.copy(file, localizedBundleDstPath, StandardCopyOption.REPLACE_EXISTING)
      return FileVisitResult.CONTINUE
    }
  })
}