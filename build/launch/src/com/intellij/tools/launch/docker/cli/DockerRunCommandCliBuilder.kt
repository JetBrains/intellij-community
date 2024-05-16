package com.intellij.tools.launch.docker.cli

import com.intellij.tools.launch.docker.cli.dsl.DockerRunCommandBuilder

private class DockerRunCommandCliBuilder : DockerRunCommandBuilder {
  private val cliOptions: MutableList<String> = mutableListOf()
  private var imageName: String? = null
  private var cliCommand: List<String>? = null

  override fun option(option: String) {
    cliOptions.add(option)
  }

  override fun option(options: List<String>) {
    cliOptions.addAll(options)
  }

  override fun options(vararg options: String) {
    cliOptions.addAll(options)
  }

  override fun bindMount(hostPath: String, containerPath: String, readOnly: Boolean) {
    cliOptions.add("--volume=$hostPath:$containerPath:${if (readOnly) "ro" else "rw"}")
  }

  override fun image(name: String) {
    imageName = name
  }

  override fun cmd(command: List<String>) {
    cliCommand = command.toList()
  }

  override fun cmd(vararg command: String) {
    cliCommand = command.toList()
  }

  fun build(): DockerRunCliCommand {
    val imageName = imageName ?: throw IllegalStateException("image name must be specified")
    return DockerRunCliCommand(cliOptions.toList(), imageName, cliCommand)
  }
}

fun dockerRunCliCommand(init: DockerRunCommandBuilder.() -> Unit): DockerRunCliCommand =
  DockerRunCommandCliBuilder().let {
    it.init()
    it.build()
  }