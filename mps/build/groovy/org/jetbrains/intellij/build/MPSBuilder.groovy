package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.fus.FeatureUsageStatisticsProperties
import org.jetbrains.intellij.build.impl.LayoutBuilder

import java.nio.file.Path

/**
 * @author victor
 */
class MPSBuilder {
    static void main(String[] args) {
        String home = args[0]

        BuildOptions options = new BuildOptions()
        options.targetOS = ""

        // Generate statistics metadata
        ProprietaryBuildTools buildTools = ProprietaryBuildTools.DUMMY
        buildTools.featureUsageStatisticsProperties = new FeatureUsageStatisticsProperties(
                "FUS", "https://resources.jetbrains.com/storage/fus/config/v4/FUS/")

        def buildContext = BuildContext.createContext("$home/community", home, new MPSProperties(home), buildTools, options)

        def buildTasks = BuildTasks.create(buildContext)
        buildTasks.compileProjectAndTests(["intellij.platform.jps.build", "intellij.platform.jps.model.tests", "intellij.platform.jps.model.serialization.tests"])
        buildTasks.buildDistributions()

        def jpsArtifactDir = "$buildContext.paths.distAll/lib/jps"
        new LayoutBuilder(buildContext).layout(jpsArtifactDir) {
            jar("jps-build-test.jar") {
                moduleTests("intellij.platform.jps.build")
                moduleTests("intellij.platform.jps.model.tests")
                moduleTests("intellij.platform.jps.model.serialization.tests")
            }
        }
        Path jpsArtifactPath = Path.of(jpsArtifactDir)
        buildContext.notifyArtifactBuilt(jpsArtifactPath)
    }
}