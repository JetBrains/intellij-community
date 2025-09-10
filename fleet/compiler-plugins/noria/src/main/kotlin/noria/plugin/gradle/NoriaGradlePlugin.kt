package noria.plugin.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

private const val VERSION = "2.2.20-RC-0.1"

class NoriaGradlePlugin : KotlinCompilerPluginSupportPlugin {
  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun getCompilerPluginId(): String = "noria"

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
      groupId = "jetbrains.fleet",
      artifactId = "noria-compiler-plugin",
      version = VERSION
  )

  override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
    return kotlinCompilation.target.project.provider { emptyList() }
  }
}