package com.intellij.tools.launch.environments

data class LaunchCommand(val commandLine: List<String>, val environment: Map<String, String>)

interface AbstractCommandLauncher<R> {
  fun launch(buildCommand: LaunchEnvironment.() -> LaunchCommand): R
}