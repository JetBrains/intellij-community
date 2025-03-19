package com.intellij.tools.launch.docker.cli

import com.intellij.tools.launch.docker.cli.dsl.DockerRunCommand

data class DockerRunCliCommandResult(val cmd: List<String>) {
  fun createProcessBuilder(): ProcessBuilder = ProcessBuilder(cmd)
}

class DockerRunCliCommand(private val cliOptions: List<String>,
                          private val imageName: String,
                          private val cliCommand: List<String>?) : DockerRunCommand<DockerRunCliCommandResult> {
  override fun launch(): DockerRunCliCommandResult {
    val cmd = buildList {
      add("docker")
      add("run")
      addAll(cliOptions)
      add(imageName)
      if (cliCommand != null) {
        addAll(cliCommand)
      }
    }
    return DockerRunCliCommandResult(cmd)
  }
}