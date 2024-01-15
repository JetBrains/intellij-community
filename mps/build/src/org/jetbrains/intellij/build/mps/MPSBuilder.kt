package org.jetbrains.intellij.build.mps

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

            val options = BuildOptions()
            options.incrementalCompilation = true
            options.useCompiledClassesFromProjectOutput = false
            options.targetOs = OsFamily.ALL
            options.validateImplicitPlatformModule = false
            options.buildStepsToSkip.add(BuildOptions.MAC_SIGN_STEP)
            options.buildStepsToSkip.add(BuildOptions.MAC_NOTARIZE_STEP)

            val fusp = FeatureUsageStatisticsProperties("FUS", "https://resources.jetbrains.com/storage/fus/config/v4/FUS/")
            val buildTools = ProprietaryBuildTools(ProprietaryBuildTools.DUMMY.signTool,
                    scrambleTool = null, macOsCodesignIdentity = null, artifactsServer = null,
                    featureUsageStatisticsProperties = listOf(fusp), licenseServerHost = null
            )

            runBlocking(Dispatchers.Default) {
                val buildContext = BuildContextImpl.createContext(
                    BuildDependenciesCommunityRoot(Path.of("$home/community")),
                    Path.of(home),
                    MPSProperties(),
                    buildTools,
                    options
                )

                val buildTasks = BuildTasks.create(buildContext)
                buildTasks.compileProjectAndTests(
                    listOf(
                        "intellij.platform.jps.build",
                        "intellij.platform.jps.build.tests",
                        "intellij.platform.jps.model.tests",
                        "intellij.platform.jps.model.serialization.tests"
                    )
                )
                buildTasks.buildDistributions()

                val binDir = buildContext.paths.getDistAll() + "/bin";
                copyFileToDir(NativeBinaryDownloader.downloadRestarter(buildContext, OsFamily.LINUX, JvmArchitecture.x64), Path.of("$binDir/linux/amd64"))
                copyFileToDir(NativeBinaryDownloader.downloadRestarter(buildContext, OsFamily.LINUX, JvmArchitecture.aarch64), Path.of("$binDir/linux/aarch64"))
                copyFileToDir(NativeBinaryDownloader.downloadRestarter(buildContext, OsFamily.MACOS, JvmArchitecture.x64), Path.of("$binDir/mac/amd64"))
                copyFileToDir(NativeBinaryDownloader.downloadRestarter(buildContext, OsFamily.MACOS, JvmArchitecture.aarch64), Path.of("$binDir/mac/aarch64"))

                val jpsArtifactDir = "${buildContext.paths.getDistAll()}/lib/jps"
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
    Files.createDirectories(targetDir)
    Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES)
}
