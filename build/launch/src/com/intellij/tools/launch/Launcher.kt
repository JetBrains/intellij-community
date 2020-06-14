package com.intellij.tools.launch

import com.intellij.tools.launch.impl.ClassPathBuilder
import java.net.InetAddress
import java.net.ServerSocket

object Launcher {

  private const val defaultDebugPort = 5050

  fun launch(paths: PathsProvider,
             modules: ModulesProvider,
             options: LauncherOptions): Process {

    val classPathBuilder = ClassPathBuilder(paths, modules)
    val classPathFile = classPathBuilder.build()

    // We should create config folder to avoid import settings dialog.
    //val configFolder = paths.configFolder
    //if (!configFolder.exists()) {
    //  configFolder.mkdirs()
    //}

    val cmd = mutableListOf(
      paths.javaExecutable.canonicalPath,
      "-ea",
      "-classpath", classPathFile.canonicalPath,
      "-Dapple.laf.useScreenMenuBar=true",
      "-Dfus.internal.test.mode=true",
      "-Didea.config.path=${paths.configFolder.canonicalPath}",
      "-Didea.system.path=${paths.systemFolder.canonicalPath}",
      "-Didea.log.path=${paths.logFolder.canonicalPath}",
      "-Didea.log.config.file=bin/log.xml",
      "-Didea.is.internal=true",
      "-Didea.debug.mode=true",
      "-Didea.jre.check=true",
      "-Didea.fix.mac.env=true",
      "-Djdk.attach.allowAttachSelf",
      "-Djdk.module.illegalAccess.silent=true",
      "-Dkotlinx.coroutines.debug=off",
      "-Dsun.awt.disablegrab=true",
      "-Dsun.io.useCanonCaches=false",
      "-Dteamcity.build.tempDir=${paths.tempFolder.canonicalPath}",
      "-Xmx${options.xmx}m",
      "-XX:+UseConcMarkSweepGC",
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
      cmd.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspendOnStart,address=$port")
    }

    for (arg in options.javaArguments) {
      cmd.add(arg.trim('"'))
    }

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

    val processBuilder = ProcessBuilder(cmd)
    if (options.redirectOutputIntoParentProcess) {
      processBuilder.inheritIO()
    } else {
      paths.logFolder.mkdirs()
      processBuilder.redirectOutput(paths.logFolder.resolve("out.log"))
      processBuilder.redirectError(paths.logFolder.resolve("err.log"))
    }

    processBuilder.environment().putAll(options.environment)
    options.beforeProcessStart.invoke(processBuilder)

    return processBuilder.start()
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