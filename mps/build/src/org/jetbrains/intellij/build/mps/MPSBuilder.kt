package org.jetbrains.intellij.build.mps

import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.fus.FeatureUsageStatisticsProperties
import org.jetbrains.intellij.build.impl.BuildContextImpl
import java.nio.file.Path

class MPSBuilder {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val home = args[0]

            val options = BuildOptions()
            options.incrementalCompilation = true
            options.useCompiledClassesFromProjectOutput = false
            options.targetOs = OsFamily.ALL

            val fusp = FeatureUsageStatisticsProperties("FUS", "https://resources.jetbrains.com/storage/fus/config/v4/FUS/")
            val buildTools = ProprietaryBuildTools(ProprietaryBuildTools.DUMMY.signTool,
                scrambleTool = null, macHostProperties = null, artifactsServer = null,
                featureUsageStatisticsProperties = fusp, licenseServerHost = null
            )

            val buildContext = BuildContextImpl.createContextBlocking(
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
            buildTasks.buildDistributionsBlocking()

            val jpsArtifactDir = "$buildContext.paths.distAll/lib/jps"
            /*
        new LayoutBuilder(buildContext).layout(jpsArtifactDir) {
            jar("jps-build-test.jar") {
                moduleTests("intellij.platform.jps.build")
                moduleTests("intellij.platform.jps.model.tests")
                moduleTests("intellij.platform.jps.model.serialization.tests")
            }
        }
 */
            val jpsArtifactPath = Path.of(jpsArtifactDir)
            buildContext.notifyArtifactBuilt(jpsArtifactPath)
        }
    }
}
