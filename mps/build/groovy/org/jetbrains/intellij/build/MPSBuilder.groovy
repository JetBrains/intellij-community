package org.jetbrains.intellij.build

import org.codehaus.gant.GantBinding
import org.jetbrains.intellij.build.fus.FeatureUsageStatisticsProperties
import org.jetbrains.intellij.build.impl.LayoutBuilder

/**
 * @author victor
 */
class MPSBuilder {
    private final GantBinding binding
    private final String home

    MPSBuilder(String home, GantBinding binding) {
        this.home = home
        this.binding = binding
    }

    def build() {
        def buildContext = BuildContext.createContext("$home/community", home, new MPSProperties(home))
        buildContext.getOptions().targetOS = ""
        ProprietaryBuildTools buildTools = ProprietaryBuildTools.DUMMY
        buildTools.featureUsageStatisticsProperties = new FeatureUsageStatisticsProperties(
                "FUS", "https://resources.jetbrains.com/storage/fus/config/v4/FUS/")
        buildContext.proprietaryBuildTools = buildTools
        def buildTasks = BuildTasks.create(buildContext)
        buildTasks.buildDistributions()

        String jpsArtifactDir = "$buildContext.paths.distAll/lib/jps"
        new LayoutBuilder(buildContext, false).layout(jpsArtifactDir) {
            jar("jps-build-test.jar") {
                moduleTests("jps-builders")
                moduleTests("jps-model-tests")
                moduleTests("jps-serialization-tests")
            }
        }
        buildContext.notifyArtifactBuilt(jpsArtifactDir)
    }
}