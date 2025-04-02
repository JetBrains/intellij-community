// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.productRunner

import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.VmProperties
import org.jetbrains.intellij.build.checkForNoDiskSpace
import org.jetbrains.intellij.build.dev.BuildRequest
import org.jetbrains.intellij.build.dev.buildProduct
import org.jetbrains.intellij.build.dev.getIdeSystemProperties
import java.nio.file.Path
import kotlin.time.Duration

/**
 * Only for use in build scripts, not for dev mode / integrations tests.
 * Use [BuildContext.createProductRunner] instead of calling this function directly.
 */
internal suspend fun createDevModeProductRunner(context: BuildContext, additionalPluginModules: List<String> = emptyList()): IntellijProductRunner {
  var newClassPath: Collection<Path>? = null
  val homeDir = context.paths.projectHome
  return checkForNoDiskSpace(context) {
    val runDir = buildProduct(
      request = BuildRequest(
        //isUnpackedDist = context.productProperties.platformPrefix != "Gateway",
        // https://youtrack.jetbrains.com/issue/IJPL-156115/devModeProductRunner-use-packed-dist-as-a-workaround-for-incorrect-product-info.json-entries-links-to-compilation-output
        isUnpackedDist = false,
        writeCoreClasspath = false,
        platformPrefix = context.productProperties.platformPrefix ?: "idea",
        additionalModules = additionalPluginModules,
        projectDir = homeDir,
        devRootDir = context.paths.tempDir.resolve("dev-run"),
        jarCacheDir = homeDir.resolve("out/dev-run/jar-cache"),
        productionClassOutput = context.classesOutputDirectory.resolve("production"),
        platformClassPathConsumer = { _, classPath, _ ->
          newClassPath = classPath
        },
        buildOptionsTemplate = context.options,
      ),
      createProductProperties = { context.productProperties }
    )
    DevModeProductRunner(context = context, homePath = runDir, classPath = newClassPath!!.map { it.toString() })
  }
}

private class DevModeProductRunner(
  private val context: BuildContext,
  private val homePath: Path,
  private val classPath: Collection<String>,
) : IntellijProductRunner {
  override suspend fun runProduct(args: List<String>, additionalVmProperties: VmProperties, timeout: Duration) {
    val multiRoutingFsBootClassPath: List<String> = if (isMultiRoutingFileSystemEnabledForProduct(context.productProperties.platformPrefix)) {
      listOf(
        "-Xbootclasspath/a:${homePath}/out/classes/production/$IJENT_BOOT_CLASSPATH_MODULE"
      )
    }
    else {
      listOf()
    }
    runApplicationStarter(
      context = context,
      classpath = classPath,
      args = args,
      timeout = timeout,
      homePath = homePath,
      vmProperties = additionalVmProperties + getIdeSystemProperties(homePath),
      isFinalClassPath = true,
      vmOptions = multiRoutingFsBootClassPath,
    )
  }
}