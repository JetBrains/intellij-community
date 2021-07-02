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

        // Avoid 'Resource not found: /idea/IdeaApplicationInfo.xml' exception from DistributionJARsBuilder#reorderJars
        buildContext.getOptions().buildStepsToSkip.add(BuildOptions.GENERATE_JAR_ORDER_STEP)

        // Generate statistics metadata
        ProprietaryBuildTools buildTools = ProprietaryBuildTools.DUMMY
        buildTools.featureUsageStatisticsProperties = new FeatureUsageStatisticsProperties(
                "FUS", "https://resources.jetbrains.com/storage/fus/config/v4/FUS/")
        buildContext.proprietaryBuildTools = buildTools

        def buildTasks = BuildTasks.create(buildContext)
        buildTasks.buildDistributions()

        String jpsArtifactDir = "$buildContext.paths.distAll/lib/jps"
        new LayoutBuilder(buildContext, false).layout(jpsArtifactDir) {
            jar("jps-build-test.jar") {
                moduleTests("intellij.platform.jps.build")
                moduleTests("intellij.platform.jps.model.tests")
                moduleTests("intellij.platform.jps.model.serialization.tests")
            }
        }
        buildContext.notifyArtifactBuilt(jpsArtifactDir)
    }
}