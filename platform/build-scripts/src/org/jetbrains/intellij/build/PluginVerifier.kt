// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.dependencies.JdkDownloader
import org.jetbrains.intellij.build.io.runProcess
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.readText


private const val DEFAULT_PLUGIN_VERIFIER_VERSION = "1.381"

suspend fun createPluginVerifier(
  pluginVerifierVersion: String = DEFAULT_PLUGIN_VERIFIER_VERSION,
  compatibilityExceptions: List<String> = emptyList(),
  exceptionHandler: (exception: String) -> Unit = {},
  errorHandler: (exception: String) -> Unit = {},
): PluginVerifier {
  val url = "https://packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-verifier/org/jetbrains/intellij/plugins/verifier-cli/$pluginVerifierVersion/verifier-cli-$pluginVerifierVersion-all.jar"
  val verifier = downloadFileToCacheLocation(url, COMMUNITY_ROOT)
  return PluginVerifier(
    verifierJar = verifier,
    compatibilityExceptions = compatibilityExceptions,
    exceptionHandler = exceptionHandler,
    errorHandler = errorHandler,
  )
}

data class VerifierPluginInfo(
  val path: Path,
  val pluginId: String,
  val buildNumber: String,
)

data class VerifierIdeInfo(
  val installationPath: Path,
  val productCode: String,
  val productBuild: String,
)

class PluginVerifier internal constructor(
  val verifierJar: Path,
  val compatibilityExceptions: List<String>,
  val exceptionHandler: (exception: String) -> Unit,
  val errorHandler: (exception: String) -> Unit,
) {

  suspend fun verify(
    homeDir: Path,
    reportDir: Path,
    plugin: VerifierPluginInfo,
    ide: VerifierIdeInfo,
    errFile: Path?,
    outFile: Path?,
  ): Boolean {
    val java = JdkDownloader.getJavaExecutable(JdkDownloader.getJdkHomeAndLog(COMMUNITY_ROOT))

    runProcess(
      args = listOf(
        java.pathString,
        "-Dplugin.verifier.home.dir=${homeDir.pathString}",
        "-jar",
        verifierJar.pathString,
        "check-plugin",
        plugin.path.pathString,
        ide.installationPath.pathString,
        "-verification-reports-dir",
        reportDir.pathString,
        "-offline"
      ),
      workingDir = reportDir,
      additionalEnvVariables = emptyMap(),
      inheritOut = false,
      inheritErrToOut = false,
      stdErrConsumer = {
        errFile?.appendText("$it\n")
        println("Plugin verifier error: $it")
      },
      stdOutConsumer = {
        outFile?.appendText("$it\n")
      }
    )

    return reportVerifierIssues(
      plugin,
      reportDir,
      ide
    )
  }

  private fun reportVerifierIssues(
    plugin: VerifierPluginInfo,
    reportDir: Path,
    ide: VerifierIdeInfo,
  ): Boolean {

    val pluginReportDir = reportDir.resolve("${ide.productCode}-${ide.productBuild}/plugins/${plugin.pluginId}/${plugin.buildNumber}")
    check(pluginReportDir.isDirectory()) {
      "Directory $pluginReportDir does not exist after running plugin verifier"
    }

    val compatibilityReport = pluginReportDir.resolve("compatibility-problems.txt")
    if (compatibilityReport.exists()) {
      val lines = compatibilityReport.readText().lines().iterator()

      var hasErrors = false

      lines.forEach { line ->
        when {
          line.startsWith("Package ") || line.startsWith("Probably the package") || line.startsWith("It is also possible") || line.startsWith("The following classes") || line.startsWith("The method might have been declared") || line.startsWith("The field might have been declared in the super classes") || line.startsWith("  ") || line.isBlank() -> { // logger.warn(line)
          }
          else -> {
            val foundExceptions = compatibilityExceptions.filter {
              line.contains(it)
            }
            if (foundExceptions.isEmpty()) {
              hasErrors = true
              errorHandler(line)
            }
            else {
              foundExceptions.forEach { exception ->
                exceptionHandler(exception)
              }
            }
          }
        }
      }
      return hasErrors
    }
    else {
      return false
    }
  }
}