package com.intellij.tools.launch.ide.environments.docker

import com.intellij.tools.launch.DockerLauncherOptions

private const val IDEA_PLATFORM_PREFIX = "idea"
private const val REMOTE_DEV_BACKEND_DEFAULT_PORT = 5990

// this should be changed to DockerLaunchOptions
internal class DockerLauncherOptionsImpl(
  override val redirectOutputIntoParentProcess: Boolean = true,
  override val dockerImageName: String,
  override val containerName: String,
) : DockerLauncherOptions {
  override val platformPrefix: String
    get() = IDEA_PLATFORM_PREFIX
  override val debugPort: Int
    get() = 5006
  override val debugSuspendOnStart: Boolean
    get() = true
  override val exposedPorts: List<Int>
    get() = listOf(debugPort, REMOTE_DEV_BACKEND_DEFAULT_PORT)
  override val runBashBeforeJava: List<String>?
    get() = null
  override val runBashBackgroundBeforeJava: List<String>?
    get() = null
}