package com.intellij.tools.launch.ide

import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.environments.LaunchEnvironment
import com.intellij.tools.launch.environments.PathInLaunchEnvironment
import com.intellij.tools.launch.ide.ClassPathBuilder.Companion.modulesToScopes

interface ClasspathCollector {
  fun collect(launchEnvironment: LaunchEnvironment): List<PathInLaunchEnvironment>
}

fun classpathCollector(
  localPaths: PathsProvider,
  mainModule: String,
  additionalRuntimeModules: List<String> = emptyList(),
  additionalTestRuntimeModules: List<String> = emptyList(),
): ClasspathCollector {
  val modulesToScopes = modulesToScopes(mainModule, additionalRuntimeModules, additionalTestRuntimeModules)
  return object : ClasspathCollector {
    override fun collect(launchEnvironment: LaunchEnvironment): List<PathInLaunchEnvironment> {
      return ClassPathBuilder(localPaths, modulesToScopes).buildClasspath(logClasspath = false) {
        launchEnvironment.fsCorrespondence().tryResolve(it) ?: error("Unable to resolve $it to the path within $launchEnvironment")
      }
    }
  }
}

fun collectedClasspath(classpath: List<String>): ClasspathCollector = CollectedClasspath(classpath)

private class CollectedClasspath(private val classpath: List<String>) : ClasspathCollector {
  override fun collect(launchEnvironment: LaunchEnvironment): List<PathInLaunchEnvironment> = classpath
}