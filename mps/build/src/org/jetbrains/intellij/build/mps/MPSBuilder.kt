package org.jetbrains.intellij.build.mps

import kotlin.io.path.exists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.fus.FeatureUsageStatisticsProperties
import org.jetbrains.intellij.build.impl.BuildContextImpl
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class MPSBuilder {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val home = args[0]

            val options = BuildOptions(
                validateImplicitPlatformModule = false,
            )
            options.incrementalCompilation = true
            options.useCompiledClassesFromProjectOutput = false
            options.targetOs = OsFamily.ALL
            options.buildStepsToSkip += listOf(BuildOptions.MAC_SIGN_STEP, BuildOptions.MAC_NOTARIZE_STEP)

            val fusp = FeatureUsageStatisticsProperties("FUS", "https://resources.jetbrains.com/storage/fus/config/v4/FUS/")
            val buildTools = ProprietaryBuildTools(ProprietaryBuildTools.DUMMY.signTool,
                    scrambleTool = null, macOsCodesignIdentity = null, artifactsServer = null,
                    featureUsageStatisticsProperties = listOf(fusp), licenseServerHost = null
            )

            runBlocking(Dispatchers.Default) {

                val buildContext = BuildContextImpl.createContext(
                    projectHome = Path.of(home),
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
