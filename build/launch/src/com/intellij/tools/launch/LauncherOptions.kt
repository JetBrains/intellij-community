package com.intellij.tools.launch

import com.intellij.platform.runtime.product.ProductMode
import java.net.InetAddress

interface LauncherOptions {
  val platformPrefix: String
  val xmx: Int get() = 800
  val debugPort: Int? get() = null
  val debugSuspendOnStart: Boolean get() = false
  val javaArguments: List<String> get() = listOf()
  val ideaArguments: List<String> get() = listOf()
  val environment: Map<String, String> get() = mapOf()
  val beforeProcessStart: () -> Unit get() = { }
  val redirectOutputIntoParentProcess: Boolean get() = false

  /**
   * Return non-null value to run the product using the module-based loading scheme in the specified mode. If `null` is returned, the old
   * path-based scheme is used.
   */
  val productMode: ProductMode? get() = null
}

data class DockerNetworkEntry(
  val name: String,
  val IPAddress: String,
  val defaultGatewayIPv4Address: String?) {
  companion object {
    val AUTO = DockerNetworkEntry("AUTO", "AUTO", "AUTO")
  }
}

interface DockerLauncherOptions : LauncherOptions {
  val exposedPorts: List<Int>
  val runBashBeforeJava: List<String>?
  val runBashBackgroundBeforeJava: List<String>?
  val address: InetAddress get() = InetAddress.getLoopbackAddress()
  val network: DockerNetworkEntry get() = DockerNetworkEntry.AUTO
  val dockerImageName: String
  val containerName: String
}