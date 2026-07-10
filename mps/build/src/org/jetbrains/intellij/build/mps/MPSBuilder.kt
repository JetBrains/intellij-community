package org.jetbrains.intellij.build.mps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.NativeBinaryDownloader
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.createBuildTasks
import org.jetbrains.intellij.build.fus.FeatureUsageStatisticsProperties
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.buildJar
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

class MPSBuilder {

    companion object {
        internal val MPS_HOME: Path = BuildPaths.COMMUNITY_ROOT.communityRoot.resolve("mps/build")

        @JvmStatic
        fun main(args: Array<String>) {
            val home = if (args.any()) {
              Path.of(args[0])
            }
            else {
              BuildPaths.MAYBE_ULTIMATE_HOME ?: BuildPaths.COMMUNITY_ROOT.communityRoot
            }

            val options = BuildOptions(
                validateImplicitPlatformModule = false,
            )
            options.incrementalCompilation = true
            options.useCompiledClassesFromProjectOutput = false
            options.targetOs = OsFamily.ALL
            options.buildStepsToSkip += listOf(BuildOptions.MAC_SIGN_STEP, BuildOptions.MAC_NOTARIZE_STEP,
                BuildOptions.WIN_SIGN_STEP)
            // needed to package JPS tests
            options.useTestCompilationOutput = true

            val fusp = FeatureUsageStatisticsProperties("FUS", "https://resources.jetbrains.com/storage/fus/config/v4/FUS/")
            val buildTools = ProprietaryBuildTools(ProprietaryBuildTools.DUMMY.signTool,
                    scrambleTool = null, artifactsServer = null,
                    featureUsageStatisticsProperties = listOf(fusp), licenseServerHost = null
            )
            @Suppress("RAW_RUN_BLOCKING")
            runBlocking(Dispatchers.Default) {

                val buildContext = BuildContextImpl.createContext(
                    projectHome = home,
                    productProperties = MPSProperties(),
                    proprietaryBuildTools = buildTools,
                    options = options
                )
                CompilationTasks.create(buildContext).compileAllModulesAndTests()
                val binDir = buildContext.paths.distAllDir.resolve("bin");

                val buildTasks = createBuildTasks(buildContext)

                buildTasks.buildDistributions()

                copyFileToDir(NativeBinaryDownloader.getRestarter(buildContext, OsFamily.LINUX, JvmArchitecture.x64), binDir.resolve("linux/amd64"))
                copyFileToDir(NativeBinaryDownloader.getRestarter(buildContext, OsFamily.LINUX, JvmArchitecture.aarch64), binDir.resolve("linux/aarch64"))
                copyFileToDir(NativeBinaryDownloader.getRestarter(buildContext, OsFamily.MACOS, JvmArchitecture.x64), binDir.resolve("mac/amd64"))
                copyFileToDir(NativeBinaryDownloader.getRestarter(buildContext, OsFamily.MACOS, JvmArchitecture.aarch64), binDir.resolve("mac/aarch64"))

                val jpsArtifactDir = "${buildContext.paths.distAllDir}/lib/jps"
                val jpsArtifactPath = Path.of(jpsArtifactDir)

                withContext(Dispatchers.IO) {
                    buildJar(
                      targetFile = jpsArtifactPath.resolve("jps-build-test.jar"),
                      moduleNames = listOf(
                        "intellij.platform.jps.build.tests",
                        "intellij.platform.jps.model.tests",
                        "intellij.platform.jps.model.serialization.tests"
                      ),
                      context = buildContext,
                      forTests = true
                    )
                  }

                buildContext.notifyArtifactBuilt(jpsArtifactPath)
            }
        }
    }
}

fun copyFileToDir(file: Path, targetDir: Path) {
    doCopyFile(file, targetDir.resolve(file.fileName), targetDir)
}

private fun doCopyFile(file: Path, target: Path, targetDir: Path) {
    var dir = Files.createDirectories(targetDir)
    var fin = Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES)
    println("TGT $target")
    println("FIN $fin")
    var ex = fin.exists()
    println("Ex $ex")
}
