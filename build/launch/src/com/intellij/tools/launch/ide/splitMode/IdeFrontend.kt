package com.intellij.tools.launch.ide.splitMode

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.ide.IdeDebugOptions
import com.intellij.tools.launch.ide.IdeLaunchContext
import com.intellij.tools.launch.ide.IdeLauncher
import com.intellij.tools.launch.ide.classpathCollector
import com.intellij.tools.launch.ide.environments.local.LocalIdeCommandLauncherFactory
import com.intellij.tools.launch.ide.environments.local.LocalProcessLaunchResult
import com.intellij.tools.launch.ide.environments.local.localLaunchOptions
import com.intellij.tools.launch.ide.splitMode.dsl.Product
import com.intellij.tools.launch.os.ProcessOutputStrategy
import kotlinx.coroutines.CoroutineScope
import java.io.File

data class IdeFrontendLaunchResult(
  val localProcessLaunchResult: LocalProcessLaunchResult,
  val debugPort: Int,
)

fun runIdeFrontendLocally(product: Product, frontendProcessLifespanScope: CoroutineScope): IdeFrontendLaunchResult {
  IdeFrontend.logger.info("Starting IDE Frontend")
  val paths = IdeFrontendIdeaPathsProvider()
  val classpath = classpathCollector(
    paths,
    mainModule = IdeConstants.PLATFORM_LOADER_MODULE,
  )
  val debugPort = 5007
  val localProcessLaunchResult = IdeLauncher.launchCommand(
    LocalIdeCommandLauncherFactory(localLaunchOptions(
      processOutputStrategy = ProcessOutputStrategy.Pipe,
      processTitle = "IDE Frontend",
      lifespanScope = frontendProcessLifespanScope
    )),
    context = IdeLaunchContext(
      classpathCollector = classpath,
      // changed in Java 9, now we have to use *: to listen on all interfaces
      localPaths = paths,
      ideDebugOptions = IdeDebugOptions(debugPort, debugSuspendOnStart = true, bindToHost = ""),
      platformPrefix = IdeConstants.JETBRAINS_CLIENT_PREFIX,
      productMode = ProductMode.FRONTEND,
      ideaArguments = listOf("thinClient", "debug://localhost:5990#newUi=true"),
      javaArguments = listOf(
        "-Dintellij.platform.root.module=${product.frontendRootProductModule}",
        "-Dintellij.platform.runtime.repository.path=${paths.outputRootFolder.toPath().resolve("module-descriptors.jar")}",
        "-D${PathManager.SYSTEM_PATHS_CUSTOMIZER}=com.intellij.platform.ide.impl.startup.multiProcess.FrontendProcessPathCustomizer",
      ),
      environment = mapOf(
        "CWM_NO_TIMEOUTS" to "1",
        "CWM_CLIENT_PASSWORD" to IdeConstants.DEFAULT_CWM_PASSWORD,
      ),
      specifyUserHomeExplicitly = false,
    )
  )
  return IdeFrontendLaunchResult(localProcessLaunchResult, debugPort)
}

private class IdeFrontendIdeaPathsProvider : PathsProvider {
  override val productId: String
    get() = IdeConstants.JETBRAINS_CLIENT_PREFIX
  override val sourcesRootFolder: File
    get() = File(PathManager.getHomePath())
  override val communityRootFolder: File
    get() = sourcesRootFolder.resolve("community")
  override val outputRootFolder: File
    get() = sourcesRootFolder.resolve("out").resolve("classes")
}

private object IdeFrontend {
  val logger = logger<IdeFrontend>()
}