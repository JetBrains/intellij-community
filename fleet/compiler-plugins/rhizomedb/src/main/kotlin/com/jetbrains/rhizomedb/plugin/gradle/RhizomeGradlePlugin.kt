package com.jetbrains.rhizomedb.plugin.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

private const val VERSION = "2.2.0-0.2"

class RhizomeGradlePlugin : KotlinCompilerPluginSupportPlugin {
  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

  override fun getCompilerPluginId(): String = "rhizomedb"

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
    groupId = "jetbrains.fleet",
    artifactId = "rhizomedb-compiler-plugin", //it's name of gradle module
    version = VERSION
  )

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    return kotlinCompilation.target.project.provider { emptyList() }
  }
}
