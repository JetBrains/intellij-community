// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.compiler.impl

import com.intellij.CommonBundle
import com.intellij.build.BuildContentManager
import com.intellij.compiler.*
import com.intellij.compiler.progress.CompilerMessagesService
import com.intellij.compiler.progress.CompilerTask
import com.intellij.compiler.server.BuildManager
import com.intellij.compiler.server.DefaultMessageHandler
import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.configurationStore.saveSettings
import com.intellij.ide.impl.runBlockingUnderModalProgress
import com.intellij.ide.nls.NlsMessages
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.compiler.*
import com.intellij.openapi.deployment.DeploymentUtil
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.DefaultModuleConfigurationEditorFactory
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.packaging.impl.compiler.ArtifactCompilerUtil
import com.intellij.packaging.impl.compiler.ArtifactsCompiler
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.tracing.Tracer
import com.intellij.util.SystemProperties
import com.intellij.util.ThrowableRunnable
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.write
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.api.*
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.BuilderMessage
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.BuilderMessage.CompileMessage
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.awt.EventQueue
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent

private val LOG = logger<CompileDriver>()
private val COMPILATION_STARTED_AUTOMATICALLY = Key.create<Boolean>("compilation_started_automatically")
private val COMPILE_SERVER_BUILD_STATUS = Key.create<ExitStatus>("COMPILE_SERVER_BUILD_STATUS")
private const val ONE_MINUTE_MS = 60L * 1000L

class CompileDriver(private val project: Project) {
  private val moduleOutputPaths = HashMap<Module, String?>()
  private val moduleTestOutputPaths = HashMap<Module, String?>()

  companion object {
    @JvmStatic
    fun setCompilationStartedAutomatically(scope: CompileScope) {
      //todo[nik] pass this option as a parameter to compile/make methods instead
      scope.putUserData(COMPILATION_STARTED_AUTOMATICALLY, true)
    }

    @TestOnly
    @JvmStatic
    fun getExternalBuildExitStatus(context: CompileContext): ExitStatus? {
      return context.getUserData(COMPILE_SERVER_BUILD_STATUS)
    }

    fun convertToCategory(kind: CompileMessage.Kind?): CompilerMessageCategory? {
      return when (kind) {
        CompileMessage.Kind.ERROR, CompileMessage.Kind.INTERNAL_BUILDER_ERROR -> CompilerMessageCategory.ERROR
        CompileMessage.Kind.WARNING -> CompilerMessageCategory.WARNING
        CompileMessage.Kind.INFO, CompileMessage.Kind.JPS_INFO, CompileMessage.Kind.OTHER -> CompilerMessageCategory.INFORMATION
        else -> null
      }
    }
  }

  fun rebuild(callback: CompileStatusNotification) {
    doRebuild(callback = callback, compileScope = ProjectCompileScope(project))
  }

  fun make(scope: CompileScope, callback: CompileStatusNotification) {
    make(scope = scope, withModalProgress = false, callback = callback)
  }

  fun make(scope: CompileScope, withModalProgress: Boolean, callback: CompileStatusNotification) {
    if (validateCompilerConfiguration(scope)) {
      startup(scope = scope, forceCompile = false, withModalProgress = withModalProgress, callback = callback)
    }
    else {
      callback.finished(aborted = true, errors = 0, warnings = 0, compileContext = DummyCompileContext.create(project))
    }
  }

  @Suppress("DuplicatedCode")
  fun isUpToDate(scope: CompileScope): Boolean {
    LOG.debug { "isUpToDate operation started" }
    val task = CompilerTask(
      project,
      JavaCompilerBundle.message("classes.up.to.date.check"),  /* headlessMode = */
      true,  /* forceAsync = */
      false,  /* waitForPreviousSession */
      false,
      isCompilationStartedAutomatically(scope)
    )
    val compileContext = CompileContextImpl(project, task, scope, true, false)
    var result: ExitStatus? = null
    val compileWork = Runnable {
      val indicator = compileContext.progressIndicator
      if (indicator.isCanceled || project.isDisposed) {
        return@Runnable
      }
      val buildManager = BuildManager.getInstance()
      try {
        buildManager.postponeBackgroundTasks()
        buildManager.cancelAutoMakeTasks(project)
        val future = compileInExternalProcess(compileContext = compileContext, onlyCheckUpToDate = true)
        if (future != null) {
          while (!future.waitFor(200L, TimeUnit.MILLISECONDS)) {
            if (indicator.isCanceled) {
              future.cancel(false)
            }
          }
        }
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
      finally {
        val exitStatus = COMPILE_SERVER_BUILD_STATUS.get(compileContext)
        task.setEndCompilationStamp(exitStatus, System.currentTimeMillis())
        result = exitStatus
        buildManager.allowBackgroundTasks(false)
        if (!project.isDisposed) {
          CompilerCacheManager.getInstance(project).flushCaches()
        }
      }
    }

    val indicatorProvider = ProgressIndicatorProvider.getInstance()
    if (!EventQueue.isDispatchThread() && indicatorProvider.progressIndicator != null) {
      // if called from background process on pooled thread, run synchronously
      task.run(compileWork, null, indicatorProvider.progressIndicator)
    }
    else {
      task.start(compileWork, null)
    }
    if (LOG.isDebugEnabled) {
      LOG.debug("isUpToDate operation finished")
    }
    return result == ExitStatus.UP_TO_DATE
  }

  @Suppress("DuplicatedCode")
  suspend fun nonBlockingIsUpToDate(scope: CompileScope): Boolean {
    val task = CompilerTask(
      project,
      JavaCompilerBundle.message("classes.up.to.date.check"),
      /* headlessMode = */ true,
      /* forceAsync = */ false,
      /* waitForPreviousSession */ false,
      isCompilationStartedAutomatically(scope)
    )

    val compileContext = CompileContextImpl(project, task, scope, true, false)
    return runUnderIndicator {
      var result: ExitStatus? = null
      task.runUsingCurrentIndicator(Runnable {
        val indicator = compileContext.progressIndicator
        if (indicator.isCanceled || project.isDisposed) {
          return@Runnable
        }

        val buildManager = BuildManager.getInstance()
        try {
          buildManager.postponeBackgroundTasks()
          buildManager.cancelAutoMakeTasks(project)
          val future = compileInExternalProcess(compileContext = compileContext, onlyCheckUpToDate = true)
          if (future != null) {
            while (!future.waitFor(200L, TimeUnit.MILLISECONDS)) {
              if (indicator.isCanceled) {
                future.cancel(false)
              }
            }
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
        finally {
          val exitStatus = COMPILE_SERVER_BUILD_STATUS.get(compileContext)
          task.setEndCompilationStamp(exitStatus, System.currentTimeMillis())
          result = exitStatus
          buildManager.allowBackgroundTasks(false)
          if (!project.isDisposed) {
            CompilerCacheManager.getInstance(project).flushCaches()
          }
        }
      }, null)
      result == ExitStatus.UP_TO_DATE
    }
  }

  fun compile(scope: CompileScope, callback: CompileStatusNotification) {
    if (validateCompilerConfiguration(scope)) {
      startup(scope = scope, forceCompile = true, callback = callback)
    }
    else {
      callback.finished(aborted = true, errors = 0, warnings = 0, compileContext = DummyCompileContext.create(project))
    }
  }

  private fun doRebuild(callback: CompileStatusNotification, compileScope: CompileScope) {
    if (validateCompilerConfiguration(compileScope)) {
      startup(scope = compileScope, isRebuild = true, forceCompile = false, callback = callback)
    }
    else {
      callback.finished(aborted = true, errors = 0, warnings = 0, compileContext = DummyCompileContext.create(project))
    }
  }

  private fun getBuildScopes(compileContext: CompileContextImpl,
                             scope: CompileScope,
                             paths: Collection<String>): List<TargetTypeBuildScope> {
    val scopes = ArrayList<TargetTypeBuildScope>()
    val forceBuild = !compileContext.isMake
    val explicitScopes = CompileScopeUtil.getBaseScopeForExternalBuild(scope)
    if (explicitScopes != null) {
      scopes.addAll(explicitScopes)
    }
    else if (!compileContext.isRebuild && (!paths.isEmpty() || !CompileScopeUtil.allProjectModulesAffected(compileContext))) {
      CompileScopeUtil.addScopesForSourceSets(scope.affectedSourceSets, scope.affectedUnloadedModules, scopes, forceBuild)
    }
    else {
      val sourceSets = scope.affectedSourceSets
      var includeTests = sourceSets.isEmpty()
      for (sourceSet in sourceSets) {
        if (sourceSet.type.isTest) {
          includeTests = true
          break
        }
      }
      if (includeTests) {
        scopes.addAll(CmdlineProtoUtil.createAllModulesScopes(forceBuild))
      }
      else {
        scopes.add(CmdlineProtoUtil.createAllModulesProductionScope(forceBuild))
      }
    }
    return if (paths.isEmpty()) mergeScopesFromProviders(scope = scope, scopes = scopes, forceBuild = forceBuild) else scopes
  }

  private fun mergeScopesFromProviders(scope: CompileScope,
                                       scopes: List<TargetTypeBuildScope>,
                                       forceBuild: Boolean): List<TargetTypeBuildScope> {
    var result = scopes
    for (provider in BuildTargetScopeProvider.EP_NAME.extensionList) {
      val providerScopes = ReadAction.compute<List<TargetTypeBuildScope>, RuntimeException> {
        if (project.isDisposed) {
          emptyList()
        }
        else {
          provider.getBuildTargetScopes(scope, project, forceBuild)
        }
      }
      result = CompileScopeUtil.mergeScopes(result, providerScopes)
    }
    return result
  }

  private fun compileInExternalProcess(compileContext: CompileContextImpl, onlyCheckUpToDate: Boolean): TaskFuture<*>? {
    val scope = compileContext.compileScope
    val paths = CompileScopeUtil.fetchFiles(compileContext)
    val scopes = getBuildScopes(compileContext = compileContext, scope = scope, paths = paths)

    // need to pass scope's user data to server
    val builderParams = HashMap<String, String>()
    if (!onlyCheckUpToDate) {
      val exported = scope.exportUserData()
      if (!exported.isEmpty()) {
        for ((key, value) in exported) {
          builderParams.put(key.toString(), value.toString())
        }
      }
    }

    if (!scope.affectedUnloadedModules.isEmpty()) {
      builderParams.put(BuildParametersKeys.LOAD_UNLOADED_MODULES, true.toString())
    }
    val outputToArtifact = if (ArtifactCompilerUtil.containsArtifacts(scopes)) {
      ArtifactCompilerUtil.createOutputToArtifactMap(project)
    }
    else {
      null
    }

    return BuildManager.getInstance().scheduleBuild(
      project,
      compileContext.isRebuild,
      compileContext.isMake,
      onlyCheckUpToDate,
      scopes,
      paths,
      builderParams,
      object : DefaultMessageHandler(project) {
        override fun sessionTerminated(sessionId: UUID) {
          if (!onlyCheckUpToDate && compileContext.shouldUpdateProblemsView()) {
            val view = ProblemsView.getInstanceIfCreated(project) ?: return
            view.clearProgress()
            view.clearOldMessages(compileContext.compileScope, compileContext.sessionId)
          }
        }

        override fun handleFailure(sessionId: UUID, failure: CmdlineRemoteProto.Message.Failure) {
          compileContext.addMessage(CompilerMessageCategory.ERROR,
                                    if (failure.hasDescription()) failure.description else "",
                                    null,
                                    -1,
                                    -1)
          val trace = if (failure.hasStacktrace()) failure.stacktrace else null
          if (trace != null) {
            LOG.info(trace)
          }
          compileContext.putUserData(COMPILE_SERVER_BUILD_STATUS, ExitStatus.ERRORS)
        }

        override fun handleCompileMessage(sessionId: UUID, message: CompileMessage) {
          val kind = message.kind
          val messageText = message.text
          if (kind == CompileMessage.Kind.PROGRESS) {
            val indicator = compileContext.progressIndicator
            indicator.text = messageText
            if (message.hasDone()) {
              indicator.fraction = message.done.toDouble()
            }
          }
          else {
            val category = convertToCategory(kind = kind) ?: CompilerMessageCategory.INFORMATION
            val sourceFilePath = if (message.hasSourceFilePath()) message.sourceFilePath?.let { FileUtil.toSystemIndependentName(it) } else null
            val line = if (message.hasLine()) message.line else -1
            val column = if (message.hasColumn()) message.column else -1
            val srcUrl = if (sourceFilePath != null) VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, sourceFilePath) else null
            compileContext.addMessage(category, messageText, srcUrl, line.toInt(), column.toInt(), null, message.moduleNamesList)
            if (compileContext.shouldUpdateProblemsView() && kind == CompileMessage.Kind.JPS_INFO) {
              // treat JPS_INFO messages in a special way: add them as info messages to the problems view
              val project = compileContext.project
              ProblemsView.getInstance(project).addMessage(CompilerMessageImpl(project, category, messageText), compileContext.sessionId)
            }
          }
        }

        override fun handleBuildEvent(sessionId: UUID, event: BuilderMessage.BuildEvent) {
          when (event.eventType) {
            BuilderMessage.BuildEvent.Type.FILES_GENERATED -> {
              val generated = event.generatedFilesList
              val publisher = if (project.isDisposed) null else project.messageBus.syncPublisher(CompilerTopics.COMPILATION_STATUS)
              val writtenArtifactOutputPaths = if (outputToArtifact == null) null else CollectionFactory.createFilePathSet()
              for (generatedFile in generated) {
                val root = FileUtil.toSystemIndependentName(generatedFile.outputRoot)
                val relativePath = FileUtil.toSystemIndependentName(generatedFile.relativePath)
                publisher?.fileGenerated(root, relativePath)
                if (outputToArtifact != null) {
                  val artifacts = outputToArtifact.get(root)
                  if (!artifacts.isNullOrEmpty()) {
                    writtenArtifactOutputPaths!!.add(FileUtil.toSystemDependentName(DeploymentUtil.appendToPath(root, relativePath)))
                  }
                }
              }
              if (!writtenArtifactOutputPaths.isNullOrEmpty()) {
                ArtifactsCompiler.addWrittenPaths(compileContext, writtenArtifactOutputPaths)
              }
            }
            BuilderMessage.BuildEvent.Type.BUILD_COMPLETED -> {
              var status = ExitStatus.SUCCESS
              if (event.hasCompletionStatus()) {
                when (event.completionStatus) {
                  BuilderMessage.BuildEvent.Status.CANCELED -> status = ExitStatus.CANCELLED
                  BuilderMessage.BuildEvent.Status.ERRORS -> status = ExitStatus.ERRORS
                  BuilderMessage.BuildEvent.Status.SUCCESS -> {}
                  BuilderMessage.BuildEvent.Status.UP_TO_DATE -> status = ExitStatus.UP_TO_DATE
                  null -> {}
                }
              }
              compileContext.putUserDataIfAbsent(COMPILE_SERVER_BUILD_STATUS, status)
            }
            BuilderMessage.BuildEvent.Type.CUSTOM_BUILDER_MESSAGE -> {
              if (event.hasCustomBuilderMessage()) {
                val message = event.customBuilderMessage
                if (GlobalOptions.JPS_SYSTEM_BUILDER_ID == message.builderId &&
                    GlobalOptions.JPS_UNPROCESSED_FS_CHANGES_MESSAGE_ID == message.messageType) {
                  val text = message.messageText
                  if (!text.isNullOrEmpty()) {
                    compileContext.addMessage(CompilerMessageCategory.INFORMATION, text, null, -1, -1)
                  }
                }
              }
            }
            null -> {
            }
          }
        }

        override fun getProgressIndicator(): ProgressIndicator = compileContext.progressIndicator
      }
    )
  }

  private fun startup(scope: CompileScope,
                      isRebuild: Boolean = false,
                      forceCompile: Boolean,
                      withModalProgress: Boolean = false,
                      callback: CompileStatusNotification?,
                      message: CompilerMessage? = null) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode
    val name = JavaCompilerBundle.message(when {
      isRebuild -> "compiler.content.name.rebuild"
      forceCompile -> "compiler.content.name.recompile"
      else -> "compiler.content.name.make"
    })
    val span = Tracer.start("$name preparation")
    val compileTask = CompilerTask(
      project, name, isUnitTestMode, !withModalProgress, true, isCompilationStartedAutomatically(scope), withModalProgress
    )
    StatusBar.Info.set("", project, "Compiler")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    FileDocumentManager.getInstance().saveAllDocuments()

    // ensure the project model seen by build process is up-to-date
    runInAutoSaveDisabledMode {
      runBlockingUnderModalProgress {
        saveSettings(project)
        if (!isUnitTestMode) {
          saveSettings(ApplicationManager.getApplication())
        }
      }
    }

    val compileContext = CompileContextImpl(project, compileTask, scope, !isRebuild && !forceCompile, isRebuild)
    span.complete()
    val compileWork = Runnable {
      val compileWorkSpan = Tracer.start("compileWork")
      val indicator = compileContext.progressIndicator
      if (indicator.isCanceled || project.isDisposed) {
        callback?.finished(true, 0, 0, compileContext)
        return@Runnable
      }

      val compilerCacheManager = CompilerCacheManager.getInstance(project)
      val buildManager = BuildManager.getInstance()
      try {
        buildManager.postponeBackgroundTasks()
        buildManager.cancelAutoMakeTasks(project)
        LOG.info("COMPILATION STARTED (BUILD PROCESS)")
        if (message != null) {
          compileContext.addMessage(message)
        }
        if (isRebuild) {
          CompilerUtil.runInContext(compileContext, JavaCompilerBundle.message("progress.text.clearing.build.system.data"),
                                    ThrowableRunnable { compilerCacheManager.clearCaches(compileContext) })
        }
        val beforeTasksOk = executeCompileTasks(context = compileContext, beforeTasks = true)
        val errorCount = compileContext.getMessageCount(CompilerMessageCategory.ERROR)
        if (!beforeTasksOk || errorCount > 0) {
          COMPILE_SERVER_BUILD_STATUS.set(compileContext, if (errorCount > 0) ExitStatus.ERRORS else ExitStatus.CANCELLED)
          return@Runnable
        }
        val future = compileInExternalProcess(compileContext, false)
        if (future != null) {
          val compileInExternalProcessSpan = Tracer.start("compile in external process")
          while (!future.waitFor(200L, TimeUnit.MILLISECONDS)) {
            if (indicator.isCanceled) {
              future.cancel(false)
            }
          }
          compileInExternalProcessSpan.complete()
          if (!executeCompileTasks(context = compileContext, beforeTasks = false)) {
            COMPILE_SERVER_BUILD_STATUS.set(compileContext, ExitStatus.CANCELLED)
          }
          if (compileContext.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
            COMPILE_SERVER_BUILD_STATUS.set(compileContext, ExitStatus.ERRORS)
          }
        }
      }
      catch (ignored: ProcessCanceledException) {
        compileContext.putUserDataIfAbsent(COMPILE_SERVER_BUILD_STATUS, ExitStatus.CANCELLED)
      }
      catch (e: Throwable) {
        LOG.error(e) // todo
      }
      finally {
        compileWorkSpan.complete()
        // reset state on explicit build to compensate possibly unbalanced postpone/allow calls
        // (e.g. via BatchFileChangeListener.start/stop)
        buildManager.allowBackgroundTasks(true)

        val flushCompilerCaches = Tracer.start("flush compiler caches")
        compilerCacheManager.flushCaches()
        flushCompilerCaches.complete()
        val duration = notifyCompilationCompleted(compileContext, callback, COMPILE_SERVER_BUILD_STATUS[compileContext])
        CompilerUtil.logDuration(
          "\tCOMPILATION FINISHED (BUILD PROCESS); Errors: " +
          compileContext.getMessageCount(CompilerMessageCategory.ERROR) +
          "; warnings: " +
          compileContext.getMessageCount(CompilerMessageCategory.WARNING),
          duration
        )
        if (ApplicationManagerEx.isInIntegrationTest()) {
          val logPath = PathManager.getLogPath()
          val perfMetrics = Paths.get(logPath).resolve("performance-metrics").resolve("buildMetrics.json")
          try {
            perfMetrics.write("""{
	"build_errors" : ${compileContext.getMessageCount(CompilerMessageCategory.ERROR)},
	"build_warnings" : ${compileContext.getMessageCount(CompilerMessageCategory.WARNING)},
	"build_compilation_duration" : $duration
}""")
          }
          catch (ex: IOException) {
            LOG.info("Could not create json file with the build performance metrics.")
          }
        }
      }
    }
    compileTask.start(compileWork) {
      if (isRebuild) {
        val rv = Messages.showOkCancelDialog(
          project, JavaCompilerBundle.message("you.are.about.to.rebuild.the.whole.project"),
          JavaCompilerBundle.message("confirm.project.rebuild"),
          CommonBundle.message("button.build"), JavaCompilerBundle.message("button.rebuild"), Messages.getQuestionIcon()
        )
        if (rv == Messages.OK /*yes, please, do run make*/) {
          startup(scope = scope, forceCompile = false, callback = callback)
          return@start
        }
      }
      startup(scope = scope, isRebuild = isRebuild, forceCompile = forceCompile, callback = callback, message = message)
    }
  }

  /**
   * @noinspection SSBasedInspection
   */
  private fun notifyCompilationCompleted(compileContext: CompileContextImpl,
                                         callback: CompileStatusNotification?,
                                         exitStatus: ExitStatus): Long {
    val endCompilationStamp = System.currentTimeMillis()
    compileContext.buildSession.setEndCompilationStamp(exitStatus, endCompilationStamp)
    val duration = endCompilationStamp - compileContext.startCompilationStamp
    if (!project.isDisposed) {
      // refresh on output roots is required in order for the order enumerator to see all roots via VFS
      val affectedModules = compileContext.compileScope.affectedModules
      if (exitStatus !== ExitStatus.UP_TO_DATE && exitStatus !== ExitStatus.CANCELLED) {
        // have to refresh in case of errors too, because run configuration may be set to ignore errors
        val affectedRoots = CompilerPaths.getOutputPaths(affectedModules).toHashSet()
        if (!affectedRoots.isEmpty()) {
          val indicator = compileContext.progressIndicator
          indicator.text = JavaCompilerBundle.message("synchronizing.output.directories")
          CompilerUtil.refreshOutputRoots(affectedRoots)
          indicator.text = ""
        }
      }
    }
    SwingUtilities.invokeLater {
      var errorCount = 0
      var warningCount = 0
      try {
        errorCount = compileContext.getMessageCount(CompilerMessageCategory.ERROR)
        warningCount = compileContext.getMessageCount(CompilerMessageCategory.WARNING)
      }
      finally {
        callback?.finished(exitStatus === ExitStatus.CANCELLED, errorCount, warningCount, compileContext)
      }
      if (!project.isDisposed) {
        val statusMessage = createStatusMessage(status = exitStatus, warningCount = warningCount, errorCount = errorCount, duration = duration)
        val messageType = if (errorCount > 0) MessageType.ERROR else if (warningCount > 0) MessageType.WARNING else MessageType.INFO
        if (duration > ONE_MINUTE_MS && CompilerWorkspaceConfiguration.getInstance(project).DISPLAY_NOTIFICATION_POPUP) {
          val toolWindowId = if (useBuildToolWindow()) BuildContentManager.TOOL_WINDOW_ID else ToolWindowId.MESSAGES_WINDOW
          ToolWindowManager.getInstance(project).notifyByBalloon(toolWindowId, messageType, statusMessage)
        }

        val wrappedMessage = if (exitStatus === ExitStatus.UP_TO_DATE) statusMessage else HtmlChunk.link("#", statusMessage).toString()
        @Suppress("DEPRECATION")
        val notification = CompilerManager.NOTIFICATION_GROUP.createNotification(wrappedMessage, messageType.toNotificationType())
          .setListener(BuildToolWindowActivationListener(compileContext))
          .setImportant(false)
        compileContext.buildSession.registerCloseAction { notification.expire() }
        notification.notify(project)
        if (exitStatus !== ExitStatus.UP_TO_DATE && compileContext.getMessageCount(null) > 0) {
          val msg = DateFormatUtil.formatDateTime(Date()) + " - " + statusMessage
          compileContext.addMessage(CompilerMessageCategory.INFORMATION, msg, null, -1, -1)
        }
      }
    }
    return duration
  }

  private fun getModuleOutputPath(module: Module, inTestSourceContent: Boolean): String? {
    val map: MutableMap<Module, String?> = if (inTestSourceContent) moduleTestOutputPaths else moduleOutputPaths
    return map.computeIfAbsent(module) { CompilerPaths.getModuleOutputPath(module, inTestSourceContent) }
  }

  fun executeCompileTask(task: CompileTask,
                         scope: CompileScope,
                         contentName: @NlsContexts.TabTitle String?,
                         onTaskFinished: Runnable?) {
    val progressManagerTask = CompilerTask(project, contentName, false, false, true, isCompilationStartedAutomatically(scope))
    val compileContext = CompileContextImpl(project, progressManagerTask, scope, false, false)
    FileDocumentManager.getInstance().saveAllDocuments()
    progressManagerTask.start({
                                try {
                                  task.execute(compileContext)
                                }
                                catch (ex: ProcessCanceledException) {
                                  // suppressed
                                }
                                finally {
                                  onTaskFinished?.run()
                                }
                              }, null)
  }

  private fun executeCompileTasks(context: CompileContext, beforeTasks: Boolean): Boolean {
    if (project.isDisposed) {
      return false
    }

    val manager = CompilerManager.getInstance(project)
    val progressIndicator = context.progressIndicator
    progressIndicator.pushState()
    try {
      val tasks = if (beforeTasks) manager.beforeTasks else manager.afterTaskList
      if (tasks.size > 0) {
        progressIndicator.text = JavaCompilerBundle.message(
          if (beforeTasks) "progress.executing.precompile.tasks" else "progress.executing.postcompile.tasks"
        )
        for (task in tasks) {
          try {
            if (!task.execute(context)) {
              return false
            }
          }
          catch (e: ProcessCanceledException) {
            throw e
          }
          catch (t: Throwable) {
            LOG.error("Error executing task", t)
            context.addMessage(CompilerMessageCategory.INFORMATION,
                               JavaCompilerBundle.message("error.task.0.execution.failed", task.toString()),
                               null,
                               -1,
                               -1)
          }
        }
      }
    }
    finally {
      progressIndicator.popState()
      WindowManager.getInstance().getStatusBar(project)?.let {
        it.info = ""
      }
    }
    return true
  }

  private fun validateCompilerConfiguration(scope: CompileScope): Boolean {
    try {
      val scopeModules = scope.affectedModules
      val compilerManager = CompilerManager.getInstance(project)
      val modulesWithSources = scopeModules.filter { module ->
        if (!compilerManager.isValidationEnabled(module)) {
          return@filter false
        }

        val hasSources = hasSources(module, JavaSourceRootType.SOURCE)
        val hasTestSources = hasSources(module, JavaSourceRootType.TEST_SOURCE)
        // If module contains no sources, shouldn't have to select JDK or output directory (SCR #19333)
        // todo still there may be problems with this approach if some generated files are attributed by this module
        hasSources || hasTestSources
      }
      if (!validateJdks(modulesWithSources, true)) {
        return false
      }
      if (!validateOutputs(modulesWithSources)) {
        return false
      }
      return validateCyclicDependencies(scopeModules)
    }
    catch (ignore: ProcessCanceledException) {
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
    return false
  }

  private fun validateJdks(scopeModules: List<Module>, runUnknownSdkCheck: Boolean): Boolean {
    val modulesWithoutJdkAssigned = ArrayList<String?>()
    var projectSdkNotSpecified = false
    for (module in scopeModules) {
      val jdk = ModuleRootManager.getInstance(module).sdk
      if (jdk != null) {
        continue
      }

      projectSdkNotSpecified = projectSdkNotSpecified or ModuleRootManager.getInstance(module).isSdkInherited
      modulesWithoutJdkAssigned.add(module.name)
    }

    if (runUnknownSdkCheck) {
      val result = CompilerDriverUnknownSdkTracker
        .getInstance(project)
        .fixSdkSettings(projectSdkNotSpecified, scopeModules, formatModulesList(modulesWithoutJdkAssigned))
      return if (result === CompilerDriverUnknownSdkTracker.Outcome.STOP_COMPILE) false else validateJdks(scopeModules, false)
      //we do not trust the CompilerDriverUnknownSdkTracker, to extra check has to be done anyway
    }
    else {
      if (modulesWithoutJdkAssigned.isEmpty()) {
        return true
      }
      showNotSpecifiedError("error.jdk.not.specified", projectSdkNotSpecified, modulesWithoutJdkAssigned,
                            JavaCompilerBundle.message("modules.classpath.title"))
      return false
    }
  }

  private fun validateOutputs(scopeModules: List<Module>): Boolean {
    val modulesWithoutOutputPathSpecified = ArrayList<String?>()
    var projectOutputNotSpecified = false
    for (module in scopeModules) {
      val outputPath = getModuleOutputPath(module = module, inTestSourceContent = false)
      val testsOutputPath = getModuleOutputPath(module = module, inTestSourceContent = true)
      if (outputPath == null && testsOutputPath == null) {
        val compilerExtension = CompilerModuleExtension.getInstance(module)
        projectOutputNotSpecified = projectOutputNotSpecified or (compilerExtension != null && compilerExtension.isCompilerOutputPathInherited)
        modulesWithoutOutputPathSpecified.add(module.name)
      }
      else {
        if (outputPath == null) {
          if (hasSources(module, JavaSourceRootType.SOURCE)) {
            modulesWithoutOutputPathSpecified.add(module.name)
          }
        }
        if (testsOutputPath == null) {
          if (hasSources(module, JavaSourceRootType.TEST_SOURCE)) {
            modulesWithoutOutputPathSpecified.add(module.name)
          }
        }
      }
    }
    if (modulesWithoutOutputPathSpecified.isEmpty()) {
      return true
    }
    showNotSpecifiedError("error.output.not.specified", projectOutputNotSpecified, modulesWithoutOutputPathSpecified,
                          DefaultModuleConfigurationEditorFactory.getInstance().outputEditorDisplayName)
    return false
  }

  private fun validateCyclicDependencies(scopeModules: Array<Module>): Boolean {
    val chunks = ModuleCompilerUtil.getCyclicDependencies(project, scopeModules.asList())
    for (chunk in chunks) {
      val sourceSets = chunk.nodes
      if (sourceSets.size <= 1) {
        // no need to check one-module chunks
        continue
      }

      var jdk: Sdk? = null
      var languageLevel: LanguageLevel? = null
      for (sourceSet in sourceSets) {
        val module = sourceSet.module
        val moduleJdk = ModuleRootManager.getInstance(module).sdk
        if (jdk == null) {
          jdk = moduleJdk
        }
        else {
          if (jdk != moduleJdk) {
            showCyclicModulesErrorNotification("error.chunk.modules.must.have.same.jdk", ModuleSourceSet.getModules(sourceSets))
            return false
          }
        }
        val moduleLanguageLevel = LanguageLevelUtil.getEffectiveLanguageLevel(module)
        if (languageLevel == null) {
          languageLevel = moduleLanguageLevel
        }
        else {
          if (languageLevel != moduleLanguageLevel) {
            showCyclicModulesErrorNotification("error.chunk.modules.must.have.same.language.level", ModuleSourceSet.getModules(sourceSets))
            return false
          }
        }
      }
    }
    return true
  }

  private fun showCyclicModulesErrorNotification(messageId: @PropertyKey(resourceBundle = JavaCompilerBundle.BUNDLE) String,
                                                 modulesInChunk: Set<Module>) {
    val firstModule = modulesInChunk.first()
    CompileDriverNotifications.getInstance(project)
      .createCannotStartNotification()
      .withContent(JavaCompilerBundle.message(messageId, getModulesString(modulesInChunk)))
      .withOpenSettingsAction(firstModule.name, null)
      .showNotification()
  }

  private fun showNotSpecifiedError(resourceId: @PropertyKey(resourceBundle = JavaCompilerBundle.BUNDLE) @NonNls String?,
                                    notSpecifiedValueInheritedFromProject: Boolean,
                                    modules: List<String?>,
                                    editorNameToSelect: String) {
    val nameToSelect = if (notSpecifiedValueInheritedFromProject) null else modules.first()
    val message = JavaCompilerBundle.message(resourceId!!, modules.size, formatModulesList(modules))
    if (ApplicationManager.getApplication().isUnitTestMode) {
      LOG.error(message)
    }
    CompileDriverNotifications.getInstance(project)
      .createCannotStartNotification()
      .withContent(message)
      .withOpenSettingsAction(nameToSelect, editorNameToSelect)
      .showNotification()
  }
}

private fun useBuildToolWindow(): Boolean {
  return SystemProperties.getBooleanProperty("ide.jps.use.build.tool.window", true)
}

private class BuildToolWindowActivationListener(compileContext: CompileContextImpl) : NotificationListener.Adapter() {
  private val projectRef: WeakReference<Project>
  private val contentId: Any

  init {
    projectRef = WeakReference(compileContext.project)
    contentId = compileContext.buildSession.contentId
  }

  override fun hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
    val project = projectRef.get()
    val useBuildToolwindow = useBuildToolWindow()
    val toolWindowId = if (useBuildToolwindow) BuildContentManager.TOOL_WINDOW_ID else ToolWindowId.MESSAGES_WINDOW
    if (project != null && !project.isDisposed &&
        (useBuildToolwindow || CompilerMessagesService.showCompilerContent(project, contentId))) {
      ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)?.activate(null, false)
    }
    else {
      notification.expire()
    }
  }
}

private fun isCompilationStartedAutomatically(scope: CompileScope): Boolean {
  return java.lang.Boolean.TRUE == scope.getUserData(COMPILATION_STARTED_AUTOMATICALLY)
}

private fun getModulesString(modulesInChunk: Collection<Module>): String {
  return modulesInChunk.joinToString(separator = "\n") { "\"" + it.name + "\"" }
}

private fun hasSources(module: Module, rootType: JavaSourceRootType): Boolean {
  return !ModuleRootManager.getInstance(module).getSourceRoots(rootType).isEmpty()
}

private fun formatModulesList(modules: List<String?>): String {
  val maxModulesToShow = 10
  val actualNamesToInclude: MutableList<String?> = ArrayList(ContainerUtil.getFirstItems(modules, maxModulesToShow))
  if (modules.size > maxModulesToShow) {
    actualNamesToInclude.add(JavaCompilerBundle.message("error.jdk.module.names.overflow.element.ellipsis"))
  }
  return NlsMessages.formatNarrowAndList(actualNamesToInclude)
}

private fun createStatusMessage(status: ExitStatus, warningCount: Int, errorCount: Int, duration: Long): @Nls String {
  return when {
    status === ExitStatus.CANCELLED -> JavaCompilerBundle.message("status.compilation.aborted")
    status === ExitStatus.UP_TO_DATE -> JavaCompilerBundle.message("status.all.up.to.date")
    else -> {
      val durationString = NlsMessages.formatDurationApproximate(duration)
      if (status === ExitStatus.SUCCESS) {
        if (warningCount > 0) {
          JavaCompilerBundle.message("status.compilation.completed.successfully.with.warnings", warningCount, durationString)
        }
        else {
          JavaCompilerBundle.message("status.compilation.completed.successfully", durationString)
        }
      }
      else {
        JavaCompilerBundle.message("status.compilation.completed.successfully.with.warnings.and.errors",
                                   errorCount, warningCount, durationString)
      }
    }
  }
}