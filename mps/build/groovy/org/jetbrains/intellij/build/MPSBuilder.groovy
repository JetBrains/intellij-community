package org.jetbrains.intellij.build

import org.codehaus.gant.GantBinding

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
        def buildContext = BuildContext.createContext(
                binding.ant, binding.projectBuilder, binding.project, binding.global,
                "$home/community", home, new MPSProperties(home)
        )
        buildContext.getOptions().targetOS = ""
        def buildTasks = BuildTasks.create(buildContext)
        buildTasks.buildDistributions()
    }
}