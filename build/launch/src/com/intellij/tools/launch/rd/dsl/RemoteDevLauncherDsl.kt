package com.intellij.tools.launch.rd.dsl

import com.intellij.tools.launch.rd.components.RemoteDevConstants
import java.nio.file.Path

interface LaunchRemoteDevBuilder {
  fun client(init: JetBrainsClientBuilder.() -> Unit)

  fun docker(init: DockerBuilder.() -> Unit)

  fun backend(product: Product, init: BackendBuilder.() -> Unit)
}

interface IdeBuilder {
  fun attachDebugger(callback: suspend (Int) -> Unit)
}

enum class Product(val mainModule: String) {
  IDEA_ULTIMATE(mainModule = RemoteDevConstants.INTELLIJ_IDEA_ULTIMATE_MAIN_MODULE),
}

interface BackendBuilder : IdeBuilder {
  /**
   * The path to the project on the machine where backend is launched.
   *
   * Note! This does not add the bind mount for the Docker case.
   */
  fun project(path: String)
}

interface DockerBuilder {
  fun image(name: String)

  fun backend(product: Product, init: DockerBackendBuilder.() -> Unit)

  fun bindMount(hostPath: Path, containerPath: String, readonly: Boolean = false)
}

interface DockerBackendBuilder : BackendBuilder

interface JetBrainsClientBuilder : IdeBuilder
