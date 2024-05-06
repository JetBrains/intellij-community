package com.intellij.tools.launch.ide.environments.local

import com.intellij.openapi.diagnostic.logger
import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.environments.*
import com.intellij.tools.launch.ide.ClassPathBuilder
import com.intellij.tools.launch.ide.ClasspathCollector
import com.intellij.tools.launch.ide.IdeCommandLauncherFactory
import com.intellij.tools.launch.ide.IdePathsInLaunchEnvironment
import com.intellij.tools.launch.os.affixIO
import com.intellij.util.SystemProperties
import com.sun.security.auth.module.UnixSystem
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object LocalLaunchEnvironment : LaunchEnvironment {
  override fun uid(): String = UnixSystem().uid.toString()
  override fun gid(): String = UnixSystem().gid.toString()
  override fun userName(): String = SystemProperties.getUserHome()
  override fun userHome(): String = SystemProperties.getUserHome()
  override fun fsCorrespondence(): FsCorrespondence = LocalFsCorrespondence

  override fun resolvePath(base: PathInLaunchEnvironment, relative: PathInLaunchEnvironment): PathInLaunchEnvironment =
    Paths.get(base, relative).toString()

  private object LocalFsCorrespondence : FsCorrespondence {
    override fun tryResolve(localPath: Path): PathInLaunchEnvironment = localPath.toString()
  }
}

interface LocalLaunchOptions {
  val beforeProcessStart: () -> Unit
  val redirectOutputIntoParentProcess: Boolean
  val logFolder: File
}

private data class LocalLaunchOptionsImpl(
  override val beforeProcessStart: () -> Unit,
  override val redirectOutputIntoParentProcess: Boolean,
  override val logFolder: File
) : LocalLaunchOptions

fun localLaunchOptions(
  beforeProcessStart: () -> Unit = {},
  redirectOutputIntoParentProcess: Boolean,
  logFolder: File
): LocalLaunchOptions =
  LocalLaunchOptionsImpl(beforeProcessStart, redirectOutputIntoParentProcess, logFolder)

class LocalCommandLauncher(private val localLaunchOptions: LocalLaunchOptions) : AbstractCommandLauncher<Process> {
  override fun launch(buildCommand: LaunchEnvironment.() -> LaunchCommand): Process {
    val (commandLine, environment) = LocalLaunchEnvironment.buildCommand()
    val processBuilder = ProcessBuilder(commandLine)

    processBuilder.affixIO(localLaunchOptions.redirectOutputIntoParentProcess, localLaunchOptions.logFolder)
    processBuilder.environment().putAll(environment)
    localLaunchOptions.beforeProcessStart()

    logger.info("Starting cmd:")
    logger.info(processBuilder.command().joinToString("\n"))
    logger.info("-- END")

    return processBuilder.start()
  }

  companion object {
    private val logger = logger<LocalCommandLauncher>()
  }
}

class LocalIdeCommandLauncherFactory(private val localLaunchOptions: LocalLaunchOptions) : IdeCommandLauncherFactory<Process> {
  override fun create(localPaths: PathsProvider, classpathCollector: ClasspathCollector): Pair<AbstractCommandLauncher<Process>, IdePathsInLaunchEnvironment> {
    val classpathInDocker = classpathCollector.collect(LocalLaunchEnvironment)
    val classPathArgFile = ClassPathBuilder.createClassPathArgFile(localPaths, classpathInDocker)
    val pathsInLocalLaunchEnvironment = object : IdePathsInLaunchEnvironment {
      override val classPathArgFile: PathInLaunchEnvironment
        get() = classPathArgFile.canonicalPath
      override val sourcesRootFolder: PathInLaunchEnvironment
        get() = localPaths.sourcesRootFolder.canonicalPath
      override val outputRootFolder: PathInLaunchEnvironment
        get() = localPaths.outputRootFolder.canonicalPath
      override val tempFolder: PathInLaunchEnvironment
        get() = localPaths.tempFolder.canonicalPath
      override val logFolder: PathInLaunchEnvironment
        get() = localPaths.logFolder.canonicalPath
      override val configFolder: PathInLaunchEnvironment
        get() = localPaths.configFolder.canonicalPath
      override val systemFolder: PathInLaunchEnvironment
        get() = localPaths.systemFolder.canonicalPath
      override val javaExecutable: PathInLaunchEnvironment
        get() = localPaths.javaExecutable.canonicalPath
    }
    return LocalCommandLauncher(localLaunchOptions) to pathsInLocalLaunchEnvironment
  }
}