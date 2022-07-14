// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.CommandLineProcessorResult.Companion.createError
import com.intellij.ide.RecentProjectsManager.Companion.getInstance
import com.intellij.ide.actions.ShowLogAction
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.OpenProjectTaskBuilder
import com.intellij.ide.impl.OpenResult
import com.intellij.ide.impl.ProjectUtil.tryOpenOrImport
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditUtil
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.idea.CommandLineArgs
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ApplicationStarter.Companion.EP_NAME
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.CommandLineProjectOpenProcessor
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.configureToOpenDotIdeaOrCreateNewIfNotExists
import com.intellij.util.PlatformUtils
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.URLUtil
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext

object CommandLineProcessor {
  private val LOG = Logger.getInstance(CommandLineProcessor::class.java)
  private const val OPTION_WAIT = "--wait"
  val OK_FUTURE: Future<CliResult> = CompletableFuture.completedFuture(CliResult.OK)

  @ApiStatus.Internal
  val SCHEME_INTERNAL = "!!!internal!!!"

  // public for testing
  @ApiStatus.Internal
  fun doOpenFileOrProject(file: Path, shouldWait: Boolean): CommandLineProcessorResult {
    var project: Project? = null
    if (!LightEditUtil.isForceOpenInLightEditMode()) {
      val options = OpenProjectTask { builder: OpenProjectTaskBuilder ->
        // do not check for .ipr files in the specified directory
        // (@develar: it is existing behaviour, I am not fully sure that it is correct)
        builder.preventIprLookup = true
        PlatformProjectOpenProcessor.Companion.configureToOpenDotIdeaOrCreateNewIfNotExists(file, null)
        Unit
      }
      val openResult = tryOpenOrImport(file, options)
      if (openResult is OpenResult.Success) {
        project = openResult.project
      }
      else if (openResult is OpenResult.Cancel) {
        return createError(IdeBundle.message("dialog.message.open.cancelled"))
      }
    }
    return if (project == null) {
      doOpenFile(file, -1, -1, false, shouldWait)
    }
    else {
      CommandLineProcessorResult(project, if (shouldWait) CommandLineWaitingManager.getInstance().addHookForProject(project) else OK_FUTURE)
    }
  }

  private fun doOpenFile(ioFile: Path, line: Int, column: Int, tempProject: Boolean, shouldWait: Boolean): CommandLineProcessorResult {
    var projects: Array<Project?> = if (tempProject) arrayOfNulls(0) else ProjectUtilCore.getOpenProjects()
    if (!tempProject && projects.size == 0 && PlatformUtils.isDataGrip()) {
      val recentProjectManager = getInstance()
      if (recentProjectManager.willReopenProjectOnStart() && GlobalScope.async(EmptyCoroutineContext,
                                                                               CoroutineStart.DEFAULT) { scope: CoroutineScope?, continuation: Continuation<Boolean?>? ->
          recentProjectManager.reopenLastProjectsOnStart(continuation)
        }.asCompletableFuture().join()) {
        projects = ProjectUtilCore.getOpenProjects()
      }
    }
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ioFile)
    if (file == null) {
      if (LightEditUtil.isLightEditEnabled()) {
        val lightEditProject = LightEditUtil.openFile(ioFile, true)
        if (lightEditProject != null) {
          val future = if (shouldWait) CommandLineWaitingManager.getInstance().addHookForPath(ioFile) else OK_FUTURE
          return CommandLineProcessorResult(lightEditProject, future)
        }
      }
      return createError(IdeBundle.message("dialog.message.can.not.open.file", ioFile.toString()))
    }
    return if (projects.size == 0) {
      val project = CommandLineProjectOpenProcessor.getInstance().openProjectAndFile(ioFile, line, column, tempProject)
                    ?: return createError(IdeBundle.message("dialog.message.no.project.found.to.open.file.in"))
      CommandLineProcessorResult(project, if (shouldWait) CommandLineWaitingManager.getInstance().addHookForFile(file) else OK_FUTURE)
    }
    else {
      NonProjectFileWritingAccessProvider.allowWriting(listOf(file))
      val project: Project?
      if (LightEditUtil.isForceOpenInLightEditMode()) {
        project = LightEditService.getInstance().openFile(file)
        LightEditFeatureUsagesUtil.logFileOpen(project, OpenPlace.CommandLine)
      }
      else {
        project = findBestProject(file, projects)
        val navigatable = if (line > 0) OpenFileDescriptor(project!!, file, line - 1, Math.max(column, 0))
        else PsiNavigationSupport.getInstance().createNavigatable(
          project!!, file, -1)
        AppUIExecutor.onUiThread().expireWith(project).execute { navigatable.navigate(true) }
      }
      CommandLineProcessorResult(project, if (shouldWait) CommandLineWaitingManager.getInstance().addHookForFile(file) else OK_FUTURE)
    }
  }

  private fun findBestProject(file: VirtualFile, projects: Array<Project?>): Project? {
    for (project in projects) {
      val fileIndex = ProjectFileIndex.getInstance(project!!)
      if (ReadAction.compute<Boolean, RuntimeException> { fileIndex.isInContent(file) }) {
        return project
      }
    }
    val frame = IdeFocusManager.getGlobalInstance().lastFocusedFrame
    if (frame != null) {
      val project = frame.project
      if (project != null && !LightEdit.owns(project)) {
        return project
      }
    }
    return projects[0]
  }

  @ApiStatus.Internal
  fun processProtocolCommand(rawUri: @NlsSafe String): CompletableFuture<CliResult> {
    LOG.info("external URI request:\n$rawUri")
    check(!ApplicationManager.getApplication().isHeadlessEnvironment) { "cannot process URI requests in headless state" }
    val internal = rawUri.startsWith(SCHEME_INTERNAL)
    val uri = if (internal) rawUri.substring(SCHEME_INTERNAL.length) else rawUri
    val separatorStart = uri.indexOf(URLUtil.SCHEME_SEPARATOR)
    require(separatorStart >= 0) { uri }
    val scheme = uri.substring(0, separatorStart)
    val query = uri.substring(separatorStart + URLUtil.SCHEME_SEPARATOR.length)
    val result = CompletableFuture<CliResult>()
    ProgressManager.getInstance().run(object : Backgroundable(null, IdeBundle.message("ide.protocol.progress.title"), true) {
      override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        indicator.text = uri
        (if (internal) processInternalProtocol(query) else ProtocolHandler.process(scheme, query, indicator))
          .exceptionally { t: Throwable ->
            LOG.error(t)
            CliResult(0, IdeBundle.message("ide.protocol.exception", t.javaClass.simpleName, t.message))
          }
          .thenAccept { cliResult: CliResult ->
            result.complete(cliResult)
            if (cliResult.message != null) {
              val title = IdeBundle.message("ide.protocol.cannot.title")
              Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, title, cliResult.message!!, NotificationType.WARNING)
                .addAction(ShowLogAction.notificationAction())
                .notify(null)
            }
          }
      }
    })
    return result
  }

  private fun processInternalProtocol(query: String): CompletableFuture<CliResult> {
    return try {
      val decoder = QueryStringDecoder(query)
      if ("open" == StringUtil.trimEnd(decoder.path(), '/')) {
        val parameters = decoder.parameters()
        val fileStr = ContainerUtil.getLastItem(parameters["file"])
        if (fileStr != null && !fileStr.isBlank()) {
          val file = parseFilePath(fileStr, null)
          if (file != null) {
            val line = StringUtil.parseInt(ContainerUtil.getLastItem(
              parameters["line"]), -1)
            val column = StringUtil.parseInt(ContainerUtil.getLastItem(
              parameters["column"]), -1)
            val (project) = openFileOrProject(file, line, column, false, false, false)
            LifecycleUsageTriggerCollector.onProtocolOpenCommandHandled(project)
            return CompletableFuture.completedFuture(CliResult.OK)
          }
        }
      }
      CompletableFuture.completedFuture(CliResult(0, IdeBundle.message("ide.protocol.internal.bad.query", query)))
    }
    catch (t: Throwable) {
      CompletableFuture.failedFuture(t)
    }
  }

  fun processExternalCommandLine(args: List<String>, currentDirectory: String?): CommandLineProcessorResult {
    val logMessage = StringBuilder()
    logMessage.append("External command line:").append('\n')
    logMessage.append("Dir: ").append(currentDirectory).append('\n')
    for (arg in args) {
      logMessage.append(arg).append('\n')
    }
    logMessage.append("-----")
    LOG.info(logMessage.toString())
    if (args.isEmpty()) {
      return CommandLineProcessorResult(null, OK_FUTURE)
    }
    val result = processApplicationStarters(args, currentDirectory)
    return result ?: processOpenFile(args, currentDirectory)
  }

  private fun processApplicationStarters(args: List<String>, currentDirectory: String?): CommandLineProcessorResult? {
    val command = args[0]
    return EP_NAME.computeSafeIfAny { starter: ApplicationStarter ->
      if (command != starter.commandName) {
        return@computeSafeIfAny null
      }
      if (!starter.canProcessExternalCommandLine()) {
        return@computeSafeIfAny createError(IdeBundle.message("dialog.message.only.one.instance.can.be.run.at.time",
                                                              ApplicationNamesInfo.getInstance().productName))
      }
      LOG.info("Processing command with $starter")
      val requiredModality = starter.requiredModality
      if (requiredModality == ApplicationStarter.NOT_IN_EDT) {
        return@computeSafeIfAny CommandLineProcessorResult(null, starter.processExternalCommandLineAsync(args, currentDirectory))
      }
      else {
        val modalityState = if (requiredModality == ApplicationStarter.ANY_MODALITY) ModalityState.any() else ModalityState.defaultModalityState()
        val ref = AtomicReference<CommandLineProcessorResult>()
        ApplicationManager.getApplication().invokeAndWait(
          { ref.set(CommandLineProcessorResult(null, starter.processExternalCommandLineAsync(args, currentDirectory))) }, modalityState)
        return@computeSafeIfAny ref.get()
      }
    }
  }

  private fun processOpenFile(args: List<String>, currentDirectory: String?): CommandLineProcessorResult {
    var projectAndCallback: CommandLineProcessorResult? = null
    var line = -1
    var column = -1
    var tempProject = false
    val shouldWait = args.contains(OPTION_WAIT)
    var lightEditMode = false
    var i = 0
    while (i < args.size) {
      var arg = args[i]
      if (CommandLineArgs.isKnownArgument(arg) || OPTION_WAIT == arg) {
        i++
        continue
      }
      if (arg == "-l" || arg == "--line") {
        i++
        if (i == args.size) break
        line = StringUtil.parseInt(args[i], -1)
        i++
        continue
      }
      if (arg == "-c" || arg == "--column") {
        i++
        if (i == args.size) break
        column = StringUtil.parseInt(args[i], -1)
        i++
        continue
      }
      if (arg == "--temp-project") {
        tempProject = true
        i++
        continue
      }
      if (arg == "-e" || arg == "--edit") {
        lightEditMode = true
        i++
        continue
      }
      if (arg == "-p" || arg == "--project") {
        // Skip, replaced with the opposite option above
        // TODO<rv>: Remove in future versions
        i++
        continue
      }
      if (StringUtil.isQuotedString(arg)) {
        arg = StringUtil.unquoteString(arg)
      }
      val file = parseFilePath(arg, currentDirectory)
                 ?: return createError(IdeBundle.message("dialog.message.invalid.path", arg))
      projectAndCallback = openFileOrProject(file, line, column, tempProject, shouldWait, lightEditMode)
      if (shouldWait) {
        break
      }
      column = -1
      line = column
      tempProject = false
      i++
    }
    return if (projectAndCallback != null) {
      projectAndCallback
    }
    else {
      if (shouldWait) {
        return CommandLineProcessorResult(
          null,
          CliResult.error(1, IdeBundle.message("dialog.message.wait.must.be.supplied.with.file.or.project.to.wait.for"))
        )
      }
      if (lightEditMode) {
        LightEditService.getInstance().showEditorWindow()
        return CommandLineProcessorResult(LightEditService.getInstance().project, OK_FUTURE)
      }
      CommandLineProcessorResult(null, OK_FUTURE)
    }
  }

  private fun parseFilePath(path: String, currentDirectory: String?): Path? {
    return try {
      var file = Path.of(FileUtilRt.toSystemDependentName(path)) // handle paths like '/file/foo\qwe'
      if (!file.isAbsolute) {
        file = if (currentDirectory == null) file.toAbsolutePath() else Path.of(currentDirectory).resolve(file)
      }
      file.normalize()
    }
    catch (e: InvalidPathException) {
      LOG.warn(e)
      null
    }
  }

  private fun openFileOrProject(file: Path, line: Int, column: Int,
                                tempProject: Boolean, shouldWait: Boolean, lightEditMode: Boolean): CommandLineProcessorResult {
    return LightEditUtil.computeWithCommandLineOptions(shouldWait, lightEditMode) {
      val asFile = line != -1 || tempProject
      if (asFile) doOpenFile(file, line, column, tempProject, shouldWait) else doOpenFileOrProject(file, shouldWait)
    }
  }
}