package com.intellij.tools.launch.docker.cli.dsl

import com.intellij.tools.launch.os.pathNotResolvingSymlinks
import java.io.File

interface DockerRunCommand<R> {
  fun launch(): R
}

interface DockerRunCommandBuilder {
  fun option(option: String)

  fun option(options: List<String>)

  fun options(vararg options: String)

  fun bindMount(hostPath: String, containerPath: String, readOnly: Boolean = false)

  fun image(name: String)

  fun cmd(command: List<String>)

  fun cmd(vararg command: String)
}

fun DockerRunCommandBuilder.addVolume(volume: File, isWritable: Boolean) {
  val canonical = volume.canonicalPath
  bindMount(canonical, canonical, !isWritable)

  // there's no consistency as to whether symlinks are resolved in user code, so we'll try our best and provide both
  val notResolvingSymlinks = volume.pathNotResolvingSymlinks()
  if (canonical != notResolvingSymlinks)
    bindMount(notResolvingSymlinks, notResolvingSymlinks, !isWritable)
}

fun DockerRunCommandBuilder.addReadonly(volume: File): Unit = addVolume(volume, false)

fun DockerRunCommandBuilder.addReadonlyIfExists(volume: File): Unit = addVolumeIfExists(volume, false)

fun DockerRunCommandBuilder.addVolumeIfExists(volume: File, isWritable: Boolean) {
  if (volume.exists()) addVolume(volume, isWritable)
}

fun DockerRunCommandBuilder.addWriteable(volume: File) {
  addVolume(volume, true)
}