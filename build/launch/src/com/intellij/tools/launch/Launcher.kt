package com.intellij.tools.launch

import com.intellij.tools.launch.impl.ClassPathBuilder
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.logging.Logger

object Launcher {

  private const val defaultDebugPort = 5050
  private val logger = Logger.getLogger(Launcher::class.java.name)

  fun launch(paths: PathsProvider,
             modules: ModulesProvider,
             options: LauncherOptions,
             logClasspath: Boolean): Process {
    val classPathBuilder = ClassPathBuilder(paths, modules)
    logger.info("Building classpath")
    val classPathArgFile = classPathBuilder.build(logClasspath)
    logger.info("Done building classpath")

    return launch(paths, classPathArgFile, options)
  }

  fun launch(paths: PathsProvider,
             classPathArgFile: File,
             options: LauncherOptions): Process {

    // We should create config folder to avoid import settings dialog.
    Files.createDirectories(paths.configFolder.toPath())

    val cmd = mutableListOf(
      paths.javaExecutable.canonicalPath,
      "-ea",
      "-Dapple.laf.useScreenMenuBar=true",
      "-Dfus.internal.test.mode=true",
      "-Djb.privacy.policy.text=\"<!--999.999-->\"",
      "-Djb.consents.confirmation.enabled=false",
      "-Didea.suppress.statistics.report=true",
      "-Drsch.send.usage.stat=false",
      "-Duse.linux.keychain=false",
      "-Didea.initially.ask.config=force-not",
      "-Dide.show.tips.on.startup.default.value=false",
      "-Didea.config.path=${paths.configFolder.canonicalPath}",
      "-Didea.system.path=${paths.systemFolder.canonicalPath}",
      "-Didea.log.path=${paths.logFolder.canonicalPath}",
      "-Didea.is.internal=true",
      "-Didea.debug.mode=true",
      "-Didea.jre.check=true",
      "-Didea.fix.mac.env=true",
      "-Djdk.attach.allowAttachSelf",
      "-Djdk.module.illegalAccess.silent=true",
      "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
      "-Dkotlinx.coroutines.debug=off",
      "-Dsun.awt.disablegrab=true",
      "-Dsun.io.useCanonCaches=false",
      "-Dteamcity.build.tempDir=${paths.tempFolder.canonicalPath}",
      "-Xmx${options.xmx}m",
      "-XX:+UseG1GC",
      "-XX:-OmitStackTraceInFastThrow",
      "-XX:CICompilerCount=2",
      "-XX:HeapDumpPath=${paths.tempFolder.canonicalPath}",
      "-XX:MaxJavaStackTraceDepth=10000",
      "-XX:ReservedCodeCacheSize=240m",
      "-XX:SoftRefLRUPolicyMSPerMB=50"
    )

    if (options.platformPrefix != null) {
      cmd.add("-Didea.platform.prefix=${options.platformPrefix}")
    }

    if (!TeamCityHelper.isUnderTeamCity) {
      val suspendOnStart = if (options.debugSuspendOnStart) "y" else "n"
      val port = if (options.debugPort > 0) options.debugPort else findFreeDebugPort()

      // changed in Java 9, now we have to use *: to listen on all interfaces
      val host = if (options.runInDocker) "*:" else ""
      cmd.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspendOnStart,address=$host$port")
    }

    for (arg in options.javaArguments) {
      cmd.add(arg.trim('"'))
    }

    cmd.add("@${classPathArgFile.canonicalPath}")
    cmd.add("com.intellij.idea.Main")

    for (arg in options.ideaArguments) {
      cmd.add(arg.trim('"'))
    }

    /*
    println("Starting cmd:")
    for (arg in cmd) {
      println("  $arg")
    }
    println("-- END")
*/

    return if (options.runInDocker) {
      val docker = DockerLauncher(paths, options as DockerLauncherOptions)
      docker.assertCanRun()

      docker.runInContainer(cmd)
    }
    else {
      val processBuilder = ProcessBuilder(cmd)

      processBuilder.affixIO(options.redirectOutputIntoParentProcess, paths.logFolder)
      processBuilder.environment().putAll(options.environment)
      options.beforeProcessStart.invoke(processBuilder)

      processBuilder.start()
    }
  }

  fun ProcessBuilder.affixIO(redirectOutputIntoParentProcess: Boolean, logFolder: File) {
    if (redirectOutputIntoParentProcess) {
      this.inheritIO()
    }
    else {
      logFolder.mkdirs()
      // TODO: test logs overwrite launcher logs
      this.redirectOutput(logFolder.resolve("out.log"))
      this.redirectError(logFolder.resolve("err.log"))
    }
  }

  fun findFreeDebugPort(): Int {
    if (isDefaultPortFree()) {
      return defaultDebugPort
    }

    val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    val result = socket.localPort
    socket.reuseAddress = true
    socket.close()
    return result
  }

  private fun isDefaultPortFree(): Boolean {
    var socket: ServerSocket? = null
    try {
      socket = ServerSocket(defaultDebugPort, 0, InetAddress.getByName("127.0.0.1"))
      socket.reuseAddress = true
      return true
    }
    catch (e: Exception) {
      return false
    }
    finally {
      socket?.close()
    }
  }
}