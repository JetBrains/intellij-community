package org.jetbrains.intellij.build

import org.codehaus.gant.GantBinding
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