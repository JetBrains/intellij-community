package fleet.multiplatform.expects.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

const val VERSION = "2.2.20-RC-0.1"

class ExpectsGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = "expects-compiler-plugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
      groupId = "jetbrains.fleet",
      artifactId = "expects-compiler-plugin",
      version = VERSION
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider { listOf() }
    }
}