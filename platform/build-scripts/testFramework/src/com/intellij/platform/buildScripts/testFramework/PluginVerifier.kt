package com.intellij.platform.buildScripts.testFramework

import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.dependencies.JdkDownloader
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import org.jetbrains.intellij.build.io.runProcess
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.readText


private const val PLUGIN_VERIFIER_VERSION = "1.381"
private const val PLUGIN_VERIFIER = "https://packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-verifier/org/jetbrains/intellij/plugins/verifier-cli/$PLUGIN_VERIFIER_VERSION/verifier-cli-$PLUGIN_VERIFIER_VERSION-all.jar"

suspend fun createPluginVerifier(
  exceptionHandler: (exception: String) -> Unit = {},
  errorHandler: (exception: String) -> Unit = {},
): PluginVerifier {
  val verifier = downloadFileToCacheLocation(PLUGIN_VERIFIER, COMMUNITY_ROOT)
  return PluginVerifier(verifier, exceptionHandler, errorHandler)
}

class VerifierPluginInfo(
  val path: Path,
  val buildNumber: String,
)

class VerifierIdeInfo(
  val installationPath: Path,
  val productCode: String,
  val productBuild: String,
)

class PluginVerifier(
  val verifierJar: Path,
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
    val java = JdkDownloader.getJavaExecutable(JdkDownloader.getJdkHome(COMMUNITY_ROOT))

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

    val pluginReportDir = reportDir.resolve("${ide.productCode}-${ide.productBuild}/plugins/com.intellij.ml.llm/${plugin.buildNumber}")
    assertTrue(pluginReportDir.isDirectory()) {
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
            val foundExceptions = exceptions.filter {
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

  val exceptions = listOf(

    // Code completion, perf tests plugin
    "org.jetbrains.completion.full.line.settings.GeneralState.setEnableInIntegrationTest",

    // bugs...
    "com.intellij.psi.PsiClass",
    "com.intellij.ide.util.gotoByName.GotoActionModel.ActionWrapper.<init>",
    "com.intellij.ide.util.gotoByName.GotoActionModel.getGroupMapping",
    "com.jetbrains.rdclient.util.idea.RangeUtilKt",
    "com.intellij.lang.javascript.DialectOptionHolder",

    // new 243.1
    "com.intellij.database.run.ConsoleDataRequest.CONSOLE_DATA_REQUEST",
    "com.intellij.vcs.ShelveTitlePatch",
    "com.intellij.vcs.ShelveTitleProvider",

    // To Fix
    "com.intellij.jupyter.core.jupyter.editor.outputs.JupyterOutputDataKeyExtractorKt.getOutputsForIndex"

  )

}