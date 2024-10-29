package com.intellij.tools.launch.ide.splitMode.dsl

import java.nio.file.Path

interface LaunchIdeBuilder {
  fun frontend(product: Product, init: IdeFrontendBuilder.() -> Unit)

  fun docker(init: DockerBuilder.() -> Unit)

  fun backend(product: Product, init: IdeBackendBuilder.() -> Unit)
}

interface IdeBuilder {
  fun attachDebugger(callback: suspend (Int) -> Unit)
}

enum class Product(val backendMainModule: String,
                   val frontendRootProductModule: String) {
  IDEA_ULTIMATE(
    backendMainModule = "intellij.idea.ultimate.main",
    frontendRootProductModule = "intellij.idea.frontend"
  ),
}

interface IdeBackendBuilder : IdeBuilder {
  fun jbr(path: String)

  /**
   * The path to the project on the machine where backend is launched.
   *
   * Note! This does not add the bind mount for the Docker case.
   */
  fun project(path: String)
}

interface DockerBuilder {
  fun image(name: String)

  fun backend(product: Product, init: IdeBackendInDockerBuilder.() -> Unit)

  fun bindMount(hostPath: Path, containerPath: String, readonly: Boolean = false)
}

interface IdeBackendInDockerBuilder : IdeBackendBuilder

interface IdeFrontendBuilder : IdeBuilder
