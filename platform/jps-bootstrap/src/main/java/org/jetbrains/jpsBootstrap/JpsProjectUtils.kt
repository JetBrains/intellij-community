// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.URLUtil
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.info
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.listDirectoryEntries

object JpsProjectUtils {
  fun loadJpsProject(projectHome: Path, jdkHome: Path, kotlincHome: Path): JpsModel {
    val startTime = System.currentTimeMillis()
    val m2LocalRepository = Path.of(System.getProperty("user.home"), ".m2", "repository")
    val model = JpsElementFactory.getInstance().createModel()

    val pathVariablesConfiguration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global)
    pathVariablesConfiguration.addPathVariable(
      "MAVEN_REPOSITORY", FileUtilRt.toSystemIndependentName(m2LocalRepository.toAbsolutePath().toString()))
    // Required for various Kotlin compiler plugins
    pathVariablesConfiguration.addPathVariable("KOTLIN_BUNDLED", kotlincHome.toString())

    val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    JpsProjectLoader.loadProject(model.project, pathVariables, projectHome)
    println(
      "Loaded project $projectHome: " +
        "${model.project.modules.size} modules, " +
        "${model.project.libraryCollection.libraries.size} libraries " +
        "in ${System.currentTimeMillis() - startTime} ms")
    val sdkName = "jdk-home"
    addSdk(model, sdkName, jdkHome)
    JpsSdkTableSerializer.setSdkReference(model.project.sdkReferencesTable, sdkName, JpsJavaSdkType.INSTANCE)
    return model
  }

  fun getModuleByName(model: JpsModel, moduleName: String): JpsModule {
    return model.project.modules
      .firstOrNull { it.name == moduleName }
      ?: error("Module '$moduleName' was not found")
  }

  fun getModuleRuntimeClasspath(module: JpsModule): List<File> {
    val allOutputDirectories = module.project.modules.mapNotNull {
      JpsJavaExtensionService.getInstance().getOutputDirectory(it, false) to it
    }.toMap()

    val enumerator = getModuleRuntimeClasspathEnumerator(module)
    val roots = enumerator.classes().roots.sortedBy { it.path }
    return roots.filter { root ->
      if (root.exists()) {
        return@filter true
      }

      // Skip modules with non-existent or empty source roots
      // they're ok with missing output directory and a known case
      val m = allOutputDirectories[root]
      if (m != null) {
        val moduleWithEmptySources = m.getSourceRoots(JavaSourceRootType.SOURCE).none() ||
                                     m.sourceRoots.all { !it.path.exists() || it.path.listDirectoryEntries().isEmpty() }
        if (moduleWithEmptySources) {
          // skip it without error
          return@filter false
        }
      }

      error("Classpath element does not exist: $root")
    }
  }

  private fun getModuleRuntimeClasspathEnumerator(module: JpsModule): JpsJavaDependenciesEnumerator {
    return JpsJavaExtensionService
      .dependencies(module)
      .runtimeOnly()
      .productionOnly()
      .recursively()
      .withoutSdk()
  }

  fun getRuntimeModulesClasspath(module: JpsModule): Set<JpsModule> {
    val enumerator = getModuleRuntimeClasspathEnumerator(module)
    return enumerator.modules
  }

  private fun addSdk(model: JpsModel, sdkName: String, sdkHome: Path) {
    info("Adding SDK '$sdkName' at $sdkHome")
    JpsJavaExtensionService.getInstance().addJavaSdk(model.global, sdkName, sdkHome.toString())
    val additionalSdk = model.global.libraryCollection.findLibrary(sdkName)
      ?: throw IllegalStateException("SDK $sdkHome was not found")
    for (moduleUrl in readModulesFromReleaseFile(sdkHome)) {
      additionalSdk.addRoot(moduleUrl, JpsOrderRootType.COMPILED)
    }
  }

  private fun readModulesFromReleaseFile(jdkDir: Path): List<String> {
    val releaseFile = jdkDir.resolve("release")

    val p = Properties()
    releaseFile.inputStream().use { p.load(it) }
    val modules = p.getProperty("MODULES")

    val jbrBaseUrl = URLUtil.JRT_PROTOCOL + URLUtil.SCHEME_SEPARATOR +
      FileUtil.toSystemIndependentName(jdkDir.toFile().absolutePath) +
      URLUtil.JAR_SEPARATOR

    return ContainerUtil.map(StringUtil.split(StringUtil.unquoteString(modules), " ")) { s: String -> jbrBaseUrl + s }
  }
}
