// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.fragments

import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.ClassObject
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.compiler.JavaFragmentCompiler
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Pair
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.platform.eel.provider.utils.EelProjectUtils
import com.intellij.util.lang.JavaVersion
import org.jetbrains.jps.javac.ExternalJavacProcess
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration
import java.io.File

private val LOG = logger<DefaultJavaFragmentCompiler>()

/**
 * [JavaFragmentCompiler] without the build system: no annotation processing, javac files under the compiler system directory.
 */
internal class DefaultJavaFragmentCompiler(private val project: Project) : JavaFragmentCompiler, Disposable {
  private val javac = ForkedJavacCompiler(project) { getJavacCompilerWorkingDir() }

  override fun getJavacRuntimeSdk(): Pair<Sdk, JavaSdkVersion?> = findJavacRuntimeSdk(project)

  override fun getJavacCompilerWorkingDir(): File {
    val dir = File(CompilerPaths.getCompilerSystemDirectory(project), "javac")
    dir.mkdirs()
    return dir
  }

  override fun getAnnotationProcessingConfiguration(module: Module): AnnotationProcessingConfiguration? = null

  override fun compile(
    options: List<String>,
    platformCp: Collection<File>,
    classpath: Collection<File>,
    upgradeModulePath: Collection<File>,
    modulePath: Collection<File>,
    sourcePath: Collection<File>,
    files: Collection<File>,
    outputDir: File,
  ): Collection<ClassObject> {
    val sdk = getJavacRuntimeSdk().first
    return javac.compile(sdk, options, platformCp, classpath, upgradeModulePath, modulePath, sourcePath, files, outputDir)
  }

  override fun dispose() {
    javac.stop()
  }
}

/**
 * The default policy of `BuildManager.getJavacRuntimeSdk` without the "prefer target JDK compiler" option:
 * the highest Java SDK of the project and its modules if it can run javac, else the IDE runtime JDK for a local project.
 */
private fun findJavacRuntimeSdk(project: Project): Pair<Sdk, JavaSdkVersion?> {
  val candidates = LinkedHashSet<Sdk>()
  fun addSdk(sdk: Sdk?) {
    if (sdk != null && sdk.sdkType is JavaSdkType) {
      candidates.add(sdk)
    }
  }
  addSdk(ProjectRootManager.getInstance(project).projectSdk)
  for (module in ModuleManager.getInstance(project).modules) {
    addSdk(ModuleRootManager.getInstance(module).sdk)
  }

  val requiredVersion = ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION
  val threshold = JavaSdkVersion.fromJavaVersion(JavaVersion.compose(requiredVersion))
                  ?: throw IllegalArgumentException("Unknown JDK feature version: $requiredVersion")
  val highest = candidates.mapNotNull(::tryParseJdkVersion).maxByOrNull { it.second }
  if (highest != null && highest.second.isAtLeast(threshold)) {
    return Pair(highest.first, highest.second)
  }
  return getIdeRuntimeSdkOrErrorIfRemote(project, requiredVersion, highest?.first)
}

private fun tryParseJdkVersion(sdk: Sdk): kotlin.Pair<Sdk, JavaSdkVersion>? {
  val version = JavaVersion.tryParse(sdk.versionString) ?: return null
  val sdkVersion = JavaSdkVersion.fromJavaVersion(version) ?: return null
  return sdk to sdkVersion
}

private fun getIdeRuntimeSdkOrErrorIfRemote(project: Project, requiredFeatureVersion: Int, highestRejected: Sdk?): Pair<Sdk, JavaSdkVersion?> {
  if (!EelProjectUtils.isProjectLocal(project)) {
    val environmentName = EelProviderUtil.getEelDescriptor(project).name
    LOG.info("Failed to find compatible JDK for javac. Required JDK $requiredFeatureVersion+, environment: $environmentName, " +
             "highest rejected: ${highestRejected?.let { "${it.name} (version=${it.versionString}, home=${it.homePath})" } ?: "<none>"}. " +
             describeAvailableJdks(project))
    if (highestRejected != null) {
      throw IllegalStateException(JavaCompilerBundle.message(
        "build.manager.launch.build.process.failed.to.find.compatible.jdk.found.too.old",
        "${highestRejected.name} (${highestRejected.versionString})", environmentName, requiredFeatureVersion))
    }
    throw IllegalStateException(JavaCompilerBundle.message(
      "build.manager.launch.build.process.failed.to.find.compatible.jdk.required.version", environmentName, requiredFeatureVersion))
  }
  val sdk = ProjectJdkTable.getInstance().internalJdk
            ?: throw IllegalStateException("No compatible JDK found and ${ProjectJdkTable.getInstance().javaClass.name} provides no internal JDK")
  return Pair(sdk, JavaSdk.getInstance().getVersion(sdk))
}

private fun describeAvailableJdks(project: Project): String {
  val sdks = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())
  if (sdks.isEmpty()) {
    return "No JDKs registered in the project SDK table."
  }
  val description = sdks.joinToString("; ", "[", "]") { sdk ->
    "name=${sdk.name}, version=${sdk.versionString}, home=${sdk.homePath}, compatibleWithProject=${JdkUtil.isCompatible(sdk, project)}"
  }
  return "Available JDKs in the project SDK table: $description"
}
