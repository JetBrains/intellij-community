package com.intellij.tools.launch

import java.net.InetAddress

interface LauncherOptions {
  val platformPrefix: String?
  val xmx: Int get() = 800
  val debugPort: Int get() = -1
  val debugSuspendOnStart: Boolean get() = false
  val javaArguments: List<String> get() = listOf()
  val ideaArguments: List<String> get() = listOf()
  val environment: Map<String, String> get() = mapOf()
  val beforeProcessStart: (ProcessBuilder) -> Unit get() = { }
  val redirectOutputIntoParentProcess: Boolean get() = false
  val runInDocker: Boolean get() = false
}

data class DockerNetwork(
  val name: String,
  val IPv4Address: String,
  val defaultGatewayIPv4Address: String?) {
  companion object {
    val AUTO = DockerNetwork("AUTO", "AUTO", "AUTO")
  }
}

interface DockerLauncherOptions : LauncherOptions {
  val exposedPorts: List<Int> get() = listOf(debugPort)
  val runBashBeforeJava: String? get() = null
  val address: InetAddress get() = InetAddress.getLoopbackAddress()
  val network: DockerNetwork get() = DockerNetwork.AUTO
  val containerName: String? get() = null
}