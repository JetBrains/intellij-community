package com.intellij.tools.launch

import com.intellij.tools.launch.impl.ClassPathBuilder
import com.intellij.util.JavaModuleOptions
import com.intellij.util.system.OS
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Locale
import java.util.logging.Logger

object Launcher {

  private val logger = Logger.getLogger(Launcher::class.java.name)
  private const val STRACE_PROPERTY_KEY = "com.intellij.tools.launch.Launcher.run.under.strace"

  fun launch(paths: PathsProvider,
             modulesToScopes: Map<String, JpsJavaClasspathKind>,
             options: LauncherOptions,
             logClasspath: Boolean): Pair<Process, String?> {
    val classPathBuilder = ClassPathBuilder(paths, modulesToScopes)
    logger.info("Building classpath")
    val classPathArgFile = classPathBuilder.build(logClasspath)
    logger.info("Done building classpath")

    return launch(paths, classPathArgFile, options)
  }

  fun launch(paths: PathsProvider,
             classPathArgFile: File,
             options: LauncherOptions): Pair<Process, String?> {

    val cmd = mutableListOf(
      paths.javaExecutable.canonicalPath,
      "-ea",
      "-Dfus.internal.test.mode=true",
      "-Didea.updates.url=http://127.0.0.1", // we should not spoil jetstat, which relies on update requests
      "-Djb.privacy.policy.text=\"<!--999.999-->\"",
      "-Djb.consents.confirmation.enabled=false",
      "-Didea.suppress.statistics.report=true",
      "-Drsch.send.usage.stat=false",
      "-Duse.linux.keychain=false",
      "-Didea.initially.ask.config=never",
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
      "-XX:SoftRefLRUPolicyMSPerMB=50",
      "-XX:+UnlockDiagnosticVMOptions",
      "-XX:+BytecodeVerificationLocal",
      "-Dshared.indexes.download.auto.consent=true"
    )

    val straceValue = System.getProperty(STRACE_PROPERTY_KEY, "false")?.lowercase(Locale.ROOT) ?: "false"
    if (straceValue == "true" || straceValue == "1") {
      cmd.addAll(0,
                 listOf(
                   "strace",
                   "-f",
                   "-e", "trace=file",
                   "-o", paths.logFolder.resolve("strace.log").canonicalPath,
                 )
      )
    }

    val optionsOpenedFile = paths.communityRootFolder.resolve("platform/platform-impl/resources/META-INF/OpenedPackages.txt")
    val optionsOpenedPackages = JavaModuleOptions.readOptions(optionsOpenedFile.toPath(), OS.CURRENT)
    cmd.addAll(optionsOpenedPackages)

    if (options.platformPrefix != null) {
      cmd.add("-Didea.platform.prefix=${options.platformPrefix}")
    }

    if (!TeamCityHelper.isUnderTeamCity && options.debugPort != null) {
      val suspendOnStart = if (options.debugSuspendOnStart) "y" else "n"
      val port = options.debugPort

      // changed in Java 9, now we have to use *: to listen on all interfaces
      val host = if (options is DockerLauncherOptions) "*:" else ""
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

    return if (options is DockerLauncherOptions) {
      val docker = DockerLauncher(paths, options)
      docker.assertCanRun()

      docker.runInContainer(cmd)
    }
    else {
      val processBuilder = ProcessBuilder(cmd)

      processBuilder.affixIO(options.redirectOutputIntoParentProcess, paths.logFolder)
      processBuilder.environment().putAll(options.environment)
      options.beforeProcessStart()

      logger.info("Starting cmd:")
      logger.info(processBuilder.command().joinToString("\n"))
      logger.info("-- END")

      processBuilder.start() to null
    }
  }

  fun ProcessBuilder.affixIO(redirectOutputIntoParentProcess: Boolean, logFolder: File) {
    if (redirectOutputIntoParentProcess) {
      this.inheritIO()
    }
    else {
      logFolder.mkdirs()
      val ts = System.currentTimeMillis()
      this.redirectOutput(logFolder.resolve("out-$ts.log"))
      this.redirectError(logFolder.resolve("err-$ts.log"))
    }
  }

  fun findFreePort(): Int {
    synchronized(this) {
      val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
      val result = socket.localPort
      socket.reuseAddress = true
      socket.close()
      return result
    }
  }
}