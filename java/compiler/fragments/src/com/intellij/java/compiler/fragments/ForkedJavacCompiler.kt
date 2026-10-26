// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.fragments

import com.intellij.concurrency.resetThreadContext
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.ide.IdleTracker
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.ClassObject
import com.intellij.openapi.compiler.CompilationException
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.FileCollectionFactory
import com.intellij.util.net.NetUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool
import org.jetbrains.jps.javac.CompilationPaths
import org.jetbrains.jps.javac.DiagnosticOutputConsumer
import org.jetbrains.jps.javac.ExternalJavacManager
import org.jetbrains.jps.javac.ModulePath
import org.jetbrains.jps.javac.OutputFileConsumer
import org.jetbrains.jps.javac.OutputFileObject
import org.jetbrains.jps.javac.ast.api.JavacFileData
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.tools.Diagnostic
import javax.tools.JavaFileObject

private val LOG = logger<ForkedJavacCompiler>()

/**
 * Compiles Java sources with a javac process forked by [ExternalJavacManager].
 *
 * The manager starts on the first [compile] call, in [workingDir], and lives until [stop]. The owner must call [stop] on dispose.
 */
@ApiStatus.Internal
class ForkedJavacCompiler(private val project: Project, private val workingDir: Supplier<File?>) {
  private val lock = Any()

  @Volatile
  private var manager: ExternalJavacManager? = null

  @Throws(IOException::class, CompilationException::class)
  fun compile(
    sdk: Sdk,
    options: List<String>,
    platformCp: Collection<File>,
    classpath: Collection<File>,
    upgradeModulePath: Collection<File>,
    modulePath: Collection<File>,
    sourcePath: Collection<File>,
    files: Collection<File>,
    outputDir: File,
  ): Collection<ClassObject> {
    val javaHome = findJavaHome(sdk) ?: throw IOException("Was not able to determine JDK for project " + project.name)

    val outputCollector = OutputCollector()
    val diagnostic = DiagnosticCollector()

    val sourceRoots = FileCollectionFactory.createCanonicalFileSet()
    if (sourcePath.isNotEmpty()) {
      sourceRoots.addAll(sourcePath)
    }
    else {
      files.mapNotNullTo(sourceRoots) { it.parentFile }
    }
    val outs = mapOf(outputDir to sourceRoots)

    val javacManager = getOrStartManager()
    val paths = CompilationPaths.create(platformCp, classpath, upgradeModulePath, ModulePath.create(modulePath), sourcePath)
    // do not keep process alive in tests since every test expects all spawned processes to terminate in teardown
    val compiledOk = javacManager != null && javacManager.forkJavac(
      javaHome, -1, emptyList(), options, paths, files, outs, diagnostic, outputCollector,
      JavacCompilerTool(), CanceledStatus.NULL, !ApplicationManager.getApplication().isUnitTestMode
    ).get()

    if (!compiledOk) {
      val messages = diagnostic.diagnostics.map { d ->
        val uri = d.source?.toUri()
        CompilationException.Message(
          kindToCategory(d.kind), d.getMessage(Locale.US).orEmpty(), uri?.toURL()?.toString(), d.lineNumber.toInt(), d.columnNumber.toInt()
        )
      }
      throw CompilationException("Compilation failed", messages)
    }

    return outputCollector.classes.map { CompiledClass(it.name, it.className, it.content?.toByteArray()) }
  }

  fun stop() {
    val manager = synchronized(lock) {
      val current = manager
      manager = null
      current
    }
    manager?.stop()
  }

  /** Returns `true` if all javac processes terminated. */
  @TestOnly
  fun waitForExternalJavacToTerminate(time: Long, unit: TimeUnit): Boolean {
    return manager?.waitForAllProcessHandlers(time, unit) ?: true
  }

  @TestOnly
  fun awaitNettyThreadPoolTermination(time: Long, unit: TimeUnit): Boolean {
    return manager?.awaitNettyThreadPoolTermination(time, unit) ?: true
  }

  @Throws(IOException::class)
  private fun getOrStartManager(): ExternalJavacManager? {
    manager?.let { return it }
    synchronized(lock) {
      manager?.let { return it }
      val compilerWorkingDir = workingDir.get() ?: return null // should not happen for real projects
      val listenPort = NetUtils.findAvailableSocketPort()
      // the manager lives as long as its owner, so its event loop must not become a child of the first caller's job (e.g. the debugger's)
      val started = resetThreadContext { startManager(compilerWorkingDir, listenPort) }
      manager = started
      return started
    }
  }
}

private fun findJavaHome(sdk: Sdk): String? {
  val type = sdk.sdkType as? JavaSdkType ?: return null
  val home = sdk.homePath
  if (isJdkOrJre(home)) {
    return home
  }
  // this can be a java-dependent SDK, implementing JavaSdkType
  // hack, because there is no direct way to obtain the java sdk, this sdk depends on
  val binPath = type.getBinPath(sdk)
  val parent = if (binPath != null) File(binPath).parent else null
  return if (isJdkOrJre(parent)) parent else null
}

private fun isJdkOrJre(path: String?): Boolean = path != null && (JdkUtil.checkForJre(path) || JdkUtil.checkForJdk(path))

private fun kindToCategory(kind: Diagnostic.Kind): CompilerMessageCategory {
  return when (kind) {
    Diagnostic.Kind.ERROR -> CompilerMessageCategory.ERROR
    Diagnostic.Kind.MANDATORY_WARNING, Diagnostic.Kind.WARNING -> CompilerMessageCategory.WARNING
    Diagnostic.Kind.NOTE, Diagnostic.Kind.OTHER -> CompilerMessageCategory.INFORMATION
  }
}

@Throws(IOException::class)
private fun startManager(compilerWorkingDir: File, listenPort: Int): ExternalJavacManager {
  val manager = ExternalJavacManager(
    compilerWorkingDir, ProcessIOExecutorService.INSTANCE, Registry.intValue("compiler.external.javac.keep.alive.timeout", 5 * 60 * 1000).toLong()
  )
  manager.setWslExecutablePath(WSLDistribution.findWslExe())
  manager.start(listenPort)
  val task = IdleTask(manager)
  if (ApplicationManager.getApplication().isHeadlessEnvironment) {
    // IdleTracker starts the Swing event queue, which a headless app must not do
    val future = AppExecutorUtil.getAppScheduledExecutorService()
      .scheduleWithFixedDelay(task, IDLE_CHECK_PERIOD.toLong(), IDLE_CHECK_PERIOD.toLong(), TimeUnit.MILLISECONDS)
    task.removeIdleListener = object : AccessToken() {
      override fun finish() {
        future.cancel(false)
      }
    }
  }
  else {
    task.removeIdleListener = IdleTracker.getInstance().addIdleListener(IDLE_CHECK_PERIOD, task)
  }
  return manager
}

// check idle javac processes every 10 seconds when IDE is idle
private const val IDLE_CHECK_PERIOD = 10_000

private class IdleTask(private val manager: ExternalJavacManager) : Runnable {
  @Volatile
  var removeIdleListener: AccessToken? = null

  override fun run() {
    if (manager.isRunning) {
      manager.shutdownIdleProcesses()
    }
    else {
      removeIdleListener?.close()
    }
  }
}

private class CompiledClass(private val path: String, private val className: String?, private val bytes: ByteArray?) : ClassObject {
  override fun getPath(): String = path

  override fun getClassName(): String? = className

  override fun getContent(): ByteArray? = bytes

  override fun toString(): String = className.toString()
}

private class DiagnosticCollector : DiagnosticOutputConsumer {
  val diagnostics: MutableList<Diagnostic<out JavaFileObject>> = ArrayList()

  override fun outputLineAvailable(line: String?) {
    if (line != null && line.startsWith(ExternalJavacManager.STDERR_LINE_PREFIX)) {
      LOG.info(line.trim())
    }
  }

  override fun registerJavacFileData(data: JavacFileData) {
  }

  override fun javaFileLoaded(file: File) {
  }

  override fun customOutputData(pluginId: String, dataName: String, data: ByteArray) {
  }

  override fun report(diagnostic: Diagnostic<out JavaFileObject>) {
    diagnostics.add(diagnostic)
  }
}

private class OutputCollector : OutputFileConsumer {
  val classes: MutableList<OutputFileObject> = ArrayList()

  override fun save(fileObject: OutputFileObject) {
    classes.add(fileObject)
  }
}
