// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.Formats
import org.jetbrains.intellij.build.telemetry.use
import com.intellij.util.io.Decompressor
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.idea.maven.aether.ProgressConsumer
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsSimpleElement
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.*
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.walk

/**
 * Builds archive containing production source roots of the project modules. If `includeLibraries` is `true`, the produced
 * archive also includes sources of project-level libraries on which platform API modules from `modules` list depend on.
 */
suspend fun zipSourcesOfModules(modules: List<String>, targetFile: Path, includeLibraries: Boolean, context: BuildContext) {
  context.executeStep(
    TraceManager.spanBuilder("build module sources archives")
      .setAttribute("path", context.paths.buildOutputDir.toString())
      .setAttribute(AttributeKey.stringArrayKey("modules"), modules),
    BuildOptions.SOURCES_ARCHIVE_STEP
  ) {
    withContext(Dispatchers.IO) {
      Files.createDirectories(targetFile.parent)
      Files.deleteIfExists(targetFile)
    }
    val includedLibraries = LinkedHashSet<JpsLibrary>()
    val span = Span.current()
    if (includeLibraries) {
      val debugMapping = mutableListOf<String>()
      for (moduleName in modules) {
        val module = context.findRequiredModule(moduleName)
        // We pack sources of libraries which are included in compilation classpath for platform API modules.
        // This way we'll get source files of all libraries useful for plugin developers, and the size of the archive will be reasonable.
        if (moduleName.startsWith("intellij.platform.") && context.findModule("$moduleName.impl") != null) {
          val libraries = JpsJavaExtensionService.dependencies(module).productionOnly().compileOnly().recursivelyExportedOnly().libraries
          includedLibraries.addAll(libraries)
          libraries.mapTo(debugMapping) { "${it.name} for $moduleName" }
        }
      }
      span.addEvent(
        "collect libraries to include into archive",
        Attributes.of(AttributeKey.stringArrayKey("mapping"), debugMapping)
      )
      val librariesWithMissingSources = includedLibraries
        .asSequence()
        .map { it.asTyped(JpsRepositoryLibraryType.INSTANCE) }
        .filterNotNull()
        .filter { library -> library.getPaths(JpsOrderRootType.SOURCES).any { Files.notExists(it) } }
        .toList()
      if (!librariesWithMissingSources.isEmpty()) {
        withContext(Dispatchers.IO) {
          downloadMissingLibrarySources(librariesWithMissingSources, context)
        }
      }
    }

    val zipFileMap = LinkedHashMap<Path, String>()
    for (moduleName in modules) {
      val module = context.findRequiredModule(moduleName)
      for (root in module.getSourceRoots(JavaSourceRootType.SOURCE)) {
        if (root.file.absoluteFile.exists()) {
          val sourceFiles = filterSourceFilesOnly(root.file.name, context) { FileUtil.copyDirContent(root.file.absoluteFile, it.toFile()) }
          zipFileMap.put(sourceFiles, root.properties.packagePrefix.replace(".", "/"))
        }
      }
      for (root in module.getSourceRoots(JavaResourceRootType.RESOURCE)) {
        if (root.file.absoluteFile.exists()) {
          val sourceFiles = filterSourceFilesOnly(root.file.name, context) { FileUtil.copyDirContent(root.file.absoluteFile, it.toFile()) }
          zipFileMap.put(sourceFiles, root.properties.relativeOutputPath)
        }
      }
    }

    val libraryRootUrls = includedLibraries.flatMap { it.getRootUrls(JpsOrderRootType.SOURCES) }
    span.addEvent("include ${libraryRootUrls.size} roots from ${includedLibraries.size} libraries")
    for (url in libraryRootUrls) {
      if (url.startsWith(JpsPathUtil.JAR_URL_PREFIX) && url.endsWith(JpsPathUtil.JAR_SEPARATOR)) {
        val file = Path.of(JpsPathUtil.urlToPath(url))
        if (Files.isRegularFile(file)) {
          val size = Files.size(file)
          span.addEvent(
            file.toString(), Attributes.of(
            AttributeKey.stringKey("formattedSize"), Formats.formatFileSize(size),
            AttributeKey.longKey("bytes"), size
          )
          )
          val sourceFiles = filterSourceFilesOnly(file.fileName.toString(), context) { tempDir ->
            Decompressor.Zip(file).filter { isSourceFile(it) }.extract(tempDir)
          }
          zipFileMap.put(sourceFiles, "")
        }
        else {
          span.addEvent("skip root: file doesn't exist", Attributes.of(AttributeKey.stringKey("file"), file.toString()))
        }
      }
      else {
        span.addEvent("skip root: not a jar file", Attributes.of(AttributeKey.stringKey("url"), url))
      }
    }

    TraceManager.spanBuilder("pack")
      .setAttribute("targetFile", context.paths.buildOutputDir.relativize(targetFile).toString())
      .use {
        zipWithCompression(targetFile = targetFile, dirs = zipFileMap)
      }

    context.notifyArtifactBuilt(targetFile)
  }
}

@OptIn(ExperimentalPathApi::class)
private inline fun filterSourceFilesOnly(name: String, context: BuildContext, configure: (Path) -> Unit): Path {
  val sourceFiles = Files.createTempDirectory(context.paths.tempDir, name)
  configure(sourceFiles)
  sourceFiles.walk().forEach {
    if (!Files.isDirectory(it) && !isSourceFile(it.toString())) {
      Files.delete(it)
    }
  }
  return sourceFiles
}

private fun isSourceFile(path: String): Boolean {
  return path.endsWith(".java") && path != "module-info.java" ||
         path.endsWith(".groovy") ||
         path.endsWith(".kt") ||
         path.endsWith(".form")
}

private fun downloadMissingLibrarySources(librariesWithMissingSources: List<JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>>, context: BuildContext) {
  TraceManager.spanBuilder("download missing sources")
    .setAttribute(AttributeKey.stringArrayKey("librariesWithMissingSources"), librariesWithMissingSources.map { it.name })
    .use { span ->
      val configuration = JpsRemoteRepositoryService.getInstance().getRemoteRepositoriesConfiguration(context.project)
      val repositories = configuration?.repositories?.map { ArtifactRepositoryManager.createRemoteRepository(it.id, it.url) } ?: emptyList()
      val repositoryManager = ArtifactRepositoryManager(
        getLocalArtifactRepositoryRoot(context.projectModel.global).toFile(), repositories,
        ProgressConsumer.DEAF
      )
      for (library in librariesWithMissingSources) {
        val descriptor = library.properties.data
        span.addEvent(
          "downloading sources for library", Attributes.of(
          AttributeKey.stringKey("name"), library.name,
          AttributeKey.stringKey("mavenId"), descriptor.mavenId,
        )
        )
        val downloaded = repositoryManager.resolveDependencyAsArtifact(
          descriptor.groupId, descriptor.artifactId,
          descriptor.version, EnumSet.of(ArtifactKind.SOURCES),
          descriptor.isIncludeTransitiveDependencies,
          descriptor.excludedDependencies
        )
        span.addEvent(
          "downloaded sources for library", Attributes.of(
          AttributeKey.stringArrayKey("artifacts"), downloaded.map { it.toString() },
        )
        )
      }
    }
}

private fun getLocalArtifactRepositoryRoot(global: JpsGlobal): Path {
  JpsModelSerializationDataService.getPathVariablesConfiguration(global)!!.getUserVariableValue("MAVEN_REPOSITORY")?.let {
    return Path.of(it)
  }

  val root = System.getProperty("user.home", null)
  return if (root == null) Path.of(".m2/repository") else Path.of(root, ".m2/repository")
}
