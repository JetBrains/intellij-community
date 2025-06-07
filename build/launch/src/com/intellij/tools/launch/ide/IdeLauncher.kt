package com.intellij.tools.launch.ide

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.environments.LaunchCommand
import com.intellij.util.JavaModuleOptions
import com.intellij.util.system.OS
import java.util.*

data class IdeLaunchContext(
  /**
   * We are likely to write the list to the classpath arg file and use it with `@` prefix
   */
  val classpathCollector: ClasspathCollector,
  val localPaths: PathsProvider,
  //val classPathArgFile: PathInLaunchEnvironment,
  val ideDebugOptions: IdeDebugOptions?,

  val platformPrefix: String,
  val productMode: ProductMode? = null,
  val xmx: Int = 800,
  val javaArguments: List<String> = emptyList(),
  val ideaArguments: List<String>,
  val environment: Map<String, String>,
  /**
   * Specify user home directory explicitly using `-Duser.home` VM argument
   */
  val specifyUserHomeExplicitly: Boolean = false,
)

data class IdeDebugOptions(val debugPort: Int, val debugSuspendOnStart: Boolean, val bindToHost: String)

private const val STRACE_PROPERTY_KEY = "com.intellij.tools.launch.Launcher.run.under.strace"

@Suppress("SameParameterValue")
private fun quote(s: String): String = if (SystemInfo.isWindows) "'$s'" else "\"$s\""

/**
 * The IDE launcher:
 * - **knows** how to build the command line to launch the IDE;
 * - **should not know** about a particular environment (local or Docker) the IDE is going to be launched within.
 */
object IdeLauncher {
  fun <R> launchCommand(factory: IdeCommandLauncherFactory<R>, context: IdeLaunchContext): R {
    val (launcher, environmentPaths) = factory.create(context.localPaths, context.classpathCollector)
    return launcher.launch {
      val launchEnvironment = this@launch
      val commandLine = buildList {
        add(environmentPaths.javaExecutable)
        add("-ea")
        add("-Dfus.internal.test.mode=true")
        add("-Didea.updates.url=http://127.0.0.1") // we should not spoil jetstat, which relies on update requests
        add("-Djb.privacy.policy.text=${quote("<!--999.999-->")}")
        add("-Djb.consents.confirmation.enabled=false")
        add("-Didea.suppress.statistics.report=true")
        add("-Drsch.send.usage.stat=false")
        add("-Duse.linux.keychain=false")
        add("-Didea.initially.ask.config=never")
        add("-Didea.home.path=${environmentPaths.sourcesRootFolder}")
        add("-Didea.config.path=${environmentPaths.configFolder}")
        add("-Didea.system.path=${environmentPaths.systemFolder}")
        add("-Didea.log.path=${environmentPaths.logFolder}")
        add("-Didea.is.internal=true")
        add("-Didea.debug.mode=true")
        add("-Didea.fix.mac.env=true")
        add("-Djdk.attach.allowAttachSelf")
        add("-Djdk.module.illegalAccess.silent=true")
        add("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader")
        add("-Dkotlinx.coroutines.debug=on")
        add("-Dsun.awt.disablegrab=true")
        add("-Dsun.io.useCanonCaches=false")
        add("-Dteamcity.build.tempDir=${environmentPaths.tempFolder}")
        add("-Djava.io.tmpdir=${environmentPaths.tempFolder}")
        add("-Xmx${context.xmx}m")
        add("-XX:+UseG1GC")
        add("-XX:-OmitStackTraceInFastThrow")
        add("-XX:CICompilerCount=2")
        add("-XX:HeapDumpPath=${environmentPaths.tempFolder}")
        add("-XX:MaxJavaStackTraceDepth=10000")
        add("-XX:ReservedCodeCacheSize=240m")
        add("-XX:SoftRefLRUPolicyMSPerMB=50")
        add("-XX:+UnlockDiagnosticVMOptions")
        add("-XX:+BytecodeVerificationLocal")
        add("-Dshared.indexes.download.auto.consent=true")

        if (context.specifyUserHomeExplicitly) {
          /* the module-based loader adds JARs from Maven repository (${user.home}/.m2/repository) to the classpath, so we need to ensure that
             the proper value of 'user.home' is passed to it (otherwise, it may point to /root) */
          add("-Duser.home=${launchEnvironment.userHome()}")
        }

        val straceValue = System.getProperty(STRACE_PROPERTY_KEY, "false")?.lowercase(Locale.ROOT) ?: "false"
        if (straceValue == "true" || straceValue == "1") {
          addAll(
            index = 0,
            elements = listOf(
              "strace",
              "-f",
              "-e", "trace=file",
              "-o",
              launchEnvironment.resolvePath(environmentPaths.logFolder, relative = "strace.log")
            )
          )
        }

        val optionsOpenedFile = context.localPaths.communityRootFolder.resolve("platform/platform-impl/resources/META-INF/OpenedPackages.txt")
        val optionsOpenedPackages = JavaModuleOptions.readOptions(optionsOpenedFile.toPath(), OS.CURRENT)
        addAll(optionsOpenedPackages)

        context.platformPrefix?.let {
          add("-Didea.platform.prefix=$it")
        }
        context.productMode?.let {
          add("-Dintellij.platform.product.mode=${it.id}")
        }

        context.ideDebugOptions?.let { debugOptions ->
          val suspendOnStart = if (debugOptions.debugSuspendOnStart) "y" else "n"
          val port = debugOptions.debugPort

          // changed in Java 9, now we have to use *: to listen on all interfaces
          val host = debugOptions.bindToHost
          add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspendOnStart,address=$host$port")
        }

        for (arg in context.javaArguments) {
          add(arg.trim('"'))
        }

        add("@${environmentPaths.classPathArgFile}")
        add(
          if (context.productMode != null) "com.intellij.platform.runtime.loader.IntellijLoader"
          else "com.intellij.idea.Main"
        )

        for (arg in context.ideaArguments) {
          add(arg.trim('"'))
        }
      }
      LaunchCommand(commandLine, environment = context.environment)
    }
  }
}