// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.CommandLineProcessorResult.Companion.createError
import com.intellij.ide.actions.ShowLogAction
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil
import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditUtil
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.idea.CommandLineArgs
import com.intellij.idea.findStarter
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.CommandLineProjectOpenProcessor
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.configureToOpenDotIdeaOrCreateNewIfNotExists
import com.intellij.ui.AppIcon
import com.intellij.util.PlatformUtils
import com.intellij.util.io.URLUtil
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Frame
import java.awt.Window
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.text.ParseException
import java.util.concurrent.CancellationException
import kotlin.Result

object CommandLineProcessor {
  private val LOG = logger<CommandLineProcessor>()
  private const val OPTION_WAIT = "--wait"
  @JvmField
  val OK_FUTURE: Deferred<CliResult> = CompletableDeferred(value = CliResult.OK)

  @ApiStatus.Internal
  const val SCHEME_INTERNAL = "!!!internal!!!"

  @VisibleForTesting
  @ApiStatus.Internal
  suspend fun doOpenFileOrProject(file: Path, shouldWait: Boolean): CommandLineProcessorResult {
    if (!LightEditUtil.isForceOpenInLightEditMode()) {
      val options = OpenProjectTask {
        // do not check for .ipr files in the specified directory
        // (@develar: it is existing behaviour, I am not fully sure that it is correct)
        preventIprLookup = true
        configureToOpenDotIdeaOrCreateNewIfNotExists(projectDir = file, projectToClose = null)
      }
      try {
        val project = ProjectUtil.openOrImportAsync(file, options)
        if (project != null) {
          return CommandLineProcessorResult(
            project = project,
            future = if (shouldWait) CommandLineWaitingManager.getInstance().addHookForProject(project).asDeferred() else OK_FUTURE,
          )
        }
      }
      catch (e: ProcessCanceledException) {
        return createError(IdeBundle.message("dialog.message.open.cancelled"))
      }
    }

    return doOpenFile(ioFile = file, line = -1, column = -1, tempProject = false, shouldWait = shouldWait)
  }

  private suspend fun doOpenFile(ioFile: Path, line: Int, column: Int, tempProject: Boolean, shouldWait: Boolean): CommandLineProcessorResult {
    var projects = if (tempProject) emptyList() else ProjectManagerEx.getOpenProjects()
    if (!tempProject && projects.isEmpty() && PlatformUtils.isDataGrip()) {
      val recentProjectManager = RecentProjectsManager.getInstance()
      if (recentProjectManager.willReopenProjectOnStart() && recentProjectManager.reopenLastProjectsOnStart()) {
        projects = ProjectManagerEx.getOpenProjects()
      }
    }

    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ioFile)
    if (file == null) {
      if (LightEditUtil.isLightEditEnabled()) {
        val lightEditProject = LightEditUtil.openFile(ioFile, true)
        if (lightEditProject != null) {
          val future = if (shouldWait) CommandLineWaitingManager.getInstance().addHookForPath(ioFile).asDeferred() else OK_FUTURE
          return CommandLineProcessorResult(project = lightEditProject, future = future)
        }
      }
      return createError(IdeBundle.message("dialog.message.can.not.open.file", ioFile.toString()))
    }

    if (projects.isEmpty()) {
      val project = CommandLineProjectOpenProcessor.getInstance().openProjectAndFile(ioFile, tempProject, OpenProjectTask {
        this.line = line
        this.column = column
      }) ?: return createError(IdeBundle.message("dialog.message.no.project.found.to.open.file.in"))
      return CommandLineProcessorResult(
        project = project,
        future = if (shouldWait) CommandLineWaitingManager.getInstance().addHookForFile(file).asDeferred() else OK_FUTURE,
      )
    }

    NonProjectFileWritingAccessProvider.allowWriting(listOf(file))
    val project: Project?
    if (LightEditUtil.isForceOpenInLightEditMode()) {
      project = LightEditService.getInstance().openFile(file)
      LightEditFeatureUsagesUtil.logFileOpen(project, OpenPlace.CommandLine)
    }
    else {
      project = findBestProject(file, projects)
      val navigatable = if (line > 0) {
        OpenFileDescriptor(project, file, line - 1, column.coerceAtLeast(0))
      }
      else {
        PsiNavigationSupport.getInstance().createNavigatable(project, file, -1)
      }
      @Suppress("DEPRECATION")
      project.coroutineScope.launch(Dispatchers.EDT) {
        navigatable.navigate(true)
      }
    }
    return CommandLineProcessorResult(project, if (shouldWait) CommandLineWaitingManager.getInstance().addHookForFile(file).asDeferred() else OK_FUTURE)
  }

  private suspend fun findBestProject(file: VirtualFile, projects: List<Project>): Project {
    for (project in projects) {
      val fileIndex = ProjectFileIndex.getInstance(project)
      if (readAction { fileIndex.isInContent(file) }) {
        return project
      }
    }

    val project = IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project
    return if (project != null && !LightEdit.owns(project)) project else projects.first()
  }

  @ApiStatus.Internal
  fun scheduleProcessProtocolCommand(rawUri: @NlsSafe String) {
    @Suppress("DEPRECATION")
    ApplicationManager.getApplication().coroutineScope.launch {
      processProtocolCommand(rawUri)
    }
  }

  @ApiStatus.Internal
  suspend fun processProtocolCommand(rawUri: @NlsSafe String): CliResult {
    LOG.info("external URI request:\n$rawUri")
    check(!ApplicationManager.getApplication().isHeadlessEnvironment) { "cannot process URI requests in headless state" }
    val internal = rawUri.startsWith(SCHEME_INTERNAL)
    val uri = if (internal) rawUri.substring(SCHEME_INTERNAL.length) else rawUri
    val separatorStart = uri.indexOf(URLUtil.SCHEME_SEPARATOR)
    require(separatorStart >= 0) { uri }
    val scheme = uri.substring(0, separatorStart)
    val query = uri.substring(separatorStart + URLUtil.SCHEME_SEPARATOR.length)

    val cliResult = try {
      if (internal) processInternalProtocol(query) else processProtocol(scheme, query)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
      CliResult(0, IdeBundle.message("ide.protocol.exception", e.javaClass.simpleName, e.message))
    }

    if (cliResult.message != null) {
      val title = IdeBundle.message("ide.protocol.cannot.title")
      @Suppress("DEPRECATION")
      Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, title, cliResult.message!!, NotificationType.WARNING)
        .addAction(ShowLogAction.notificationAction())
        .notify(null)
    }
    return cliResult
  }

  private val PROTOCOL_EP_NAME = ExtensionPointName<ProtocolHandler>("com.intellij.protocolHandler")

  @Suppress("IfThenToElvis")
  private suspend fun processProtocol(scheme: String, query: String): CliResult {
    val handler = PROTOCOL_EP_NAME.lazySequence().find { scheme == it.scheme }
    return if (handler != null) handler.process(query)
           else CliResult(0, IdeBundle.message("ide.protocol.unsupported", scheme))
  }

  private suspend fun processInternalProtocol(query: String): CliResult {
    val decoder = QueryStringDecoder(query)
    if ("open" == decoder.path().trimEnd('/')) {
      val parameters = decoder.parameters()
      val fileStr = parameters["file"]?.lastOrNull()
      if (!fileStr.isNullOrBlank()) {
        val file = parseFilePath(fileStr, null)
        if (file != null) {
          val line = parameters["line"]?.lastOrNull()?.toIntOrNull() ?: -1
          val column = parameters["column"]?.lastOrNull()?.toIntOrNull() ?: -1
          val (project) = openFileOrProject(file = file,
                                            line = line,
                                            column = column,
                                            tempProject = false,
                                            shouldWait = false,
                                            lightEditMode = false)
          LifecycleUsageTriggerCollector.onProtocolOpenCommandHandled(project)
          return CliResult.OK
        }
      }
    }
    return CliResult(0, IdeBundle.message("ide.protocol.internal.bad.query", query))
  }

  suspend fun processExternalCommandLine(args: List<String>,
                                         currentDirectory: String?,
                                         focusApp: Boolean = false): CommandLineProcessorResult {
    val logMessage = StringBuilder()
    logMessage.append("External command line:").append('\n')
    logMessage.append("Dir: ").append(currentDirectory).append('\n')
    for (arg in args) {
      logMessage.append(arg).append('\n')
    }
    logMessage.append("-----")
    LOG.info(logMessage.toString())
    if (args.isEmpty()) {
      if (focusApp) {
        withContext(Dispatchers.EDT) {
          findVisibleFrame()?.let { frame ->
            AppIcon.getInstance().requestFocus(frame)
          }
        }
      }
      return CommandLineProcessorResult(project = null, future = OK_FUTURE)
    }

    processApplicationStarters(args, currentDirectory)?.let {
      // app focus is up to app starter
      return CommandLineProcessorResult(project = null, result = it)
    }

    val result = processOpenFile(args, currentDirectory)
    if (focusApp) {
      withContext(Dispatchers.EDT) {
        when {
          result.hasError -> {
            result.showError()
          }
          result.project == null -> {
            findVisibleFrame()?.let { frame ->
              AppIcon.getInstance().requestFocus(frame)
            }
          }
          else -> {
            WindowManager.getInstance().getIdeFrame(result.project)?.let {
              AppIcon.getInstance().requestFocus(it)
            }
          }
        }
      }
    }
    return result
  }

  // find a frame to activate
  internal fun findVisibleFrame(): Window? {
    // we assume that the most recently created frame is the most relevant one
    return Frame.getFrames().asList().asReversed().firstOrNull { it.isVisible }
  }

  private suspend fun processApplicationStarters(args: List<String>, currentDirectory: String?): CliResult? {
    val command = args.first()

    val starter = try {
      findStarter(command) ?: return null
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
      return null
    }

    if (!starter.canProcessExternalCommandLine()) {
      return CliResult(1, IdeBundle.message("dialog.message.only.one.instance.can.be.run.at.time",
                                            ApplicationNamesInfo.getInstance().productName))
    }

    LOG.info("Processing command with $starter")
    val requiredModality = starter.requiredModality
    if (requiredModality == ApplicationStarter.NOT_IN_EDT) {
      return starter.processExternalCommandLine(args, currentDirectory)
    }

    val modalityState = if (requiredModality == ApplicationStarter.ANY_MODALITY) {
      ModalityState.any()
    }
    else {
      ModalityState.defaultModalityState()
    }
    return withContext(Dispatchers.EDT + modalityState.asContextElement()) {
      starter.processExternalCommandLine(args, currentDirectory)
    }
  }

  private sealed class ParsingResult(
    val shouldWait: Boolean, // = args.contains(OPTION_WAIT),
    val lightEditMode: Boolean,
  )

  private class OpenProjectResult(
    val file: Path,
    val line: Int = -1,
    val column: Int = -1,
    val tempProject: Boolean = false,
    shouldWait: Boolean = false,
    lightEditMode: Boolean = false,
  ): ParsingResult(shouldWait, lightEditMode)

  private class NoProjectResult(
    shouldWait: Boolean = false,
    lightEditMode: Boolean = false,
  ): ParsingResult(shouldWait, lightEditMode)

  private suspend fun processOpenFile(
    args: List<String>,
    currentDirectory: String?
  ): CommandLineProcessorResult {
    val parsedArgsResult = parseArgs(args, currentDirectory)
    if (parsedArgsResult.isFailure) {
      when (val e = parsedArgsResult.exceptionOrNull()) {
        is ParseException -> return createError(IdeBundle.message("dialog.message.invalid.path", e.message))
        else -> error("Unexpected exception during parsing arguments: $e")
      }
    }

    val commands = parsedArgsResult.getOrNull()
    requireNotNull(commands) { "Parsed args result should have been checked for failure before" }

    var result: CommandLineProcessorResult? = null
    for (command in commands) {
        result = when (command) {
          is OpenProjectResult -> {
            openFileOrProject(file = command.file,
                              line = command.line,
                              column = command.column,
                              tempProject = command.tempProject,
                              shouldWait = command.shouldWait,
                              lightEditMode = command.lightEditMode)
          }
          is NoProjectResult -> {
            if (command.shouldWait) {
              CommandLineProcessorResult(
                project = null,
                result = CliResult(1, IdeBundle.message("dialog.message.wait.must.be.supplied.with.file.or.project.to.wait.for"))
              )
            }
            else if (command.lightEditMode) {
              LightEditService.getInstance().showEditorWindow()
              CommandLineProcessorResult(project = LightEditService.getInstance().project, future = OK_FUTURE)
            }
            else {
              CommandLineProcessorResult(project = null, future = OK_FUTURE)
            }
          }
        }
    }
    return result ?: error("Parsing result shouldn't be null at this point; args are not empty")
  }

  private fun parseArgs(
    args: List<String>,
    currentDirectory: String?,
  ): Result<List<ParsingResult>> {
    val openProjectResults = mutableListOf<OpenProjectResult>()
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
        line = args[i].toIntOrNull() ?: -1
        i++
        continue
      }
      if (arg == "-c" || arg == "--column") {
        i++
        if (i == args.size) break
        column = args[i].toIntOrNull() ?: -1
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
      if (StringUtilRt.isQuotedString(arg)) {
        arg = StringUtilRt.unquoteString(arg)
      }
      val file = parseFilePath(arg, currentDirectory) ?: return Result.failure(ParseException(arg, i))

      openProjectResults += OpenProjectResult(
        file = file,
        line = line,
        column = column,
        tempProject = tempProject,
        shouldWait = shouldWait,
        lightEditMode = lightEditMode
      )
      if (shouldWait) {
        break
      }
      column = -1
      line = column
      tempProject = false
      i++
    }

    return Result.success(
      openProjectResults.ifEmpty {
        listOf(NoProjectResult(
          shouldWait = shouldWait,
          lightEditMode = lightEditMode
        ))
      }
    )
  }

  private fun parseFilePath(path: String, currentDirectory: String?): Path? {
    try {
      // handle paths like '/file/foo\qwe'
      var file = Path.of(FileUtilRt.toSystemDependentName(path))
      if (!file.isAbsolute) {
        file = if (currentDirectory == null) file.toAbsolutePath() else Path.of(currentDirectory).resolve(file)
      }
      return file.normalize()
    }
    catch (e: InvalidPathException) {
      LOG.warn(e)
      return null
    }
  }

  private suspend fun openFileOrProject(file: Path,
                                        line: Int,
                                        column: Int,
                                        tempProject: Boolean,
                                        shouldWait: Boolean,
                                        lightEditMode: Boolean): CommandLineProcessorResult {
    return LightEditUtil.computeWithCommandLineOptions(shouldWait, lightEditMode).use {
      val asFile = line != -1 || tempProject
      if (asFile) {
        doOpenFile(file, line, column, tempProject, shouldWait)
      }
      else {
        doOpenFileOrProject(file, shouldWait)
      }
    }
  }
}
