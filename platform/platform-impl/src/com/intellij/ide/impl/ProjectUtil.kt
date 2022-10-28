// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.CommonBundle
import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.configurationStore.saveSettings
import com.intellij.execution.wsl.WslPath.Companion.isWslUncPath
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.actions.OpenFileAction
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.application.*
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.CommandLineProjectOpenProcessor
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.attachToProject
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.createOptionsToOpenDotIdeaOrCreateNewIfNotExists
import com.intellij.project.stateStore
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.ui.AppIcon
import com.intellij.ui.ComponentUtil
import com.intellij.util.ModalityUiUtil
import com.intellij.util.PathUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.basicAttributesIfExists
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import kotlin.Result

private val LOG = Logger.getInstance(ProjectUtil::class.java)
private var ourProjectsPath: String? = null

object ProjectUtil {
  const val DEFAULT_PROJECT_NAME = "default"
  private const val PROJECTS_DIR = "projects"
  private const val PROPERTY_PROJECT_PATH = "%s.project.path"

  @Deprecated("Use {@link #updateLastProjectLocation(Path)} ", ReplaceWith("updateLastProjectLocation(Path.of(projectFilePath))",
                                                                           "com.intellij.ide.impl.ProjectUtil.updateLastProjectLocation",
                                                                           "java.nio.file.Path"))
  fun updateLastProjectLocation(projectFilePath: String) {
    updateLastProjectLocation(Path.of(projectFilePath))
  }

  @JvmStatic
  fun updateLastProjectLocation(lastProjectLocation: Path) {
    var location: Path? = lastProjectLocation
    if (Files.isRegularFile(location!!)) {
      // for directory-based project storage
      location = location.parent
    }
    if (location == null) {
      // the immediate parent of the ipr file
      return
    }

    // the candidate directory to be saved
    location = location.parent
    if (location == null) {
      return
    }
    var path = location.toString()
    path = try {
      FileUtil.resolveShortWindowsName(path)
    }
    catch (e: IOException) {
      LOG.info(e)
      return
    }
    RecentProjectsManager.getInstance().lastProjectCreationLocation = PathUtil.toSystemIndependentName(path)
  }

  @Deprecated("Use {@link ProjectManager#closeAndDispose(Project)} ",
              ReplaceWith("ProjectManager.getInstance().closeAndDispose(project)", "com.intellij.openapi.project.ProjectManager"))
  @JvmStatic
  fun closeAndDispose(project: Project): Boolean {
    return ProjectManager.getInstance().closeAndDispose(project)
  }

  @JvmStatic
  fun openOrImport(path: Path, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    return openOrImport(path, OpenProjectTask().withProjectToClose(projectToClose).withForceOpenInNewFrame(forceOpenInNewFrame))
  }

  /**
   * @param path                project file path
   * @param projectToClose      currently active project
   * @param forceOpenInNewFrame forces opening in new frame
   * @return project by path if the path was recognized as IDEA project file or one of the project formats supported by
   * installed importers (regardless of opening/import result)
   * null otherwise
   */
  @JvmStatic
  fun openOrImport(path: String, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    return openOrImport(Path.of(path), OpenProjectTask().withProjectToClose(projectToClose).withForceOpenInNewFrame(forceOpenInNewFrame))
  }

  @JvmStatic
  @JvmOverloads
  fun openOrImport(file: Path, options: OpenProjectTask = OpenProjectTask()): Project? {
    return runUnderModalProgressIfIsEdt {
      openOrImportAsync(file, options)
    }
  }

  suspend fun openOrImportAsync(file: Path, options: OpenProjectTask = OpenProjectTask()): Project? {
    if (!options.forceOpenInNewFrame) {
      findAndFocusExistingProjectForPath(file)?.let {
        return it
      }
    }

    var virtualFileResult: Result<VirtualFile>? = null
    for (provider in ProjectOpenProcessor.EXTENSION_POINT_NAME.iterable) {
      if (!provider.isStrongProjectInfoHolder) {
        continue
      }

      // `PlatformProjectOpenProcessor` is not a strong project info holder, so there is no need to optimize (VFS not required)
      val virtualFile: VirtualFile = virtualFileResult?.getOrThrow() ?: blockingContext {
        ProjectUtilCore.getFileAndRefresh(file)
      }?.also {
        virtualFileResult = Result.success(it)
      } ?: return null
      if (provider.canOpenProject(virtualFile)) {
        return chooseProcessorAndOpenAsync(mutableListOf(provider), virtualFile, options)
      }
    }
    if (ProjectUtilCore.isValidProjectPath(file)) {
      // see OpenProjectTest.`open valid existing project dir with inability to attach using OpenFileAction` test about why `runConfigurators = true` is specified here
      return ProjectManagerEx.getInstanceEx().openProjectAsync(file, options.copy(runConfigurators = true))
    }

    if (!options.preventIprLookup && Files.isDirectory(file)) {
      try {
        withContext(Dispatchers.IO) {
          Files.newDirectoryStream(file)
        }.use { directoryStream ->
          for (child in directoryStream) {
            val childPath = child.toString()
            if (childPath.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
              return openProject(Path.of(childPath), options)
            }
          }
        }
      }
      catch (ignore: IOException) {
      }
    }
    var nullableVirtualFileResult: Result<VirtualFile?>? = virtualFileResult
    val processors = blockingContext {
      computeProcessors(file) {
        val capturedNullableVirtualFileResult = nullableVirtualFileResult
        if (capturedNullableVirtualFileResult != null) {
          capturedNullableVirtualFileResult.getOrThrow()
        }
        else {
          ProjectUtilCore.getFileAndRefresh(file).also {
            nullableVirtualFileResult = Result.success(it)
          }
        }
      }
    }
    if (processors.isEmpty()) {
      return null
    }

    val project: Project?
    if (processors.size == 1 && processors[0] is PlatformProjectOpenProcessor) {
      project = ProjectManagerEx.getInstanceEx().openProjectAsync(
        projectStoreBaseDir = file,
        options = options.copy(
          isNewProject = true,
          useDefaultProjectAsTemplate = true,
          runConfigurators = true,
          beforeOpen = {
            it.putUserData(PlatformProjectOpenProcessor.PROJECT_OPENED_BY_PLATFORM_PROCESSOR, true)
            true
          },
        )
      )
    }
    else {
      val virtualFile = nullableVirtualFileResult?.let {
        it.getOrThrow() ?: return null
      } ?: blockingContext {
        ProjectUtilCore.getFileAndRefresh(file)
      } ?: return null
      project = chooseProcessorAndOpenAsync(processors, virtualFile, options)
    }
    return postProcess(project)
  }

  private fun computeProcessors(file: Path, lazyVirtualFile: () -> VirtualFile?): MutableList<ProjectOpenProcessor> {
    val processors = ArrayList<ProjectOpenProcessor>()
    ProjectOpenProcessor.EXTENSION_POINT_NAME.forEachExtensionSafe { processor: ProjectOpenProcessor ->
      if (processor is PlatformProjectOpenProcessor) {
        if (Files.isDirectory(file)) {
          processors.add(processor)
        }
      }
      else {
        val virtualFile = lazyVirtualFile()
        if (virtualFile != null && processor.canOpenProject(virtualFile)) {
          processors.add(processor)
        }
      }
    }
    return processors
  }

  private fun postProcess(project: Project?): Project? {
    if (project == null) {
      return null
    }
    StartupManager.getInstance(project).runAfterOpened {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.NON_MODAL, project.disposed) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
        toolWindow?.activate(null)
      }
    }
    return project
  }

  fun openProject(path: String, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    return openProject(Path.of(path), OpenProjectTask().withProjectToClose(projectToClose).withForceOpenInNewFrame(forceOpenInNewFrame))
  }

  private suspend fun chooseProcessorAndOpenAsync(processors: MutableList<ProjectOpenProcessor>,
                                                  virtualFile: VirtualFile,
                                                  options: OpenProjectTask): Project? {
    val processor = when (processors.size) {
      1 -> {
        processors.first()
      }
      else -> {
        processors.removeIf { it is PlatformProjectOpenProcessor }
        if (processors.size == 1) {
          processors.first()
        }
        else {
          val chooser = options.processorChooser
          if (chooser == null) {
            withContext(Dispatchers.EDT) {
              SelectProjectOpenProcessorDialog.showAndGetChoice(processors, virtualFile)
            } ?: return null
          }
          else {
            LOG.info("options.openProcessorChooser will handle the open processor dilemma")
            chooser(processors) as ProjectOpenProcessor
          }
        }
      }
    }

    processor.openProjectAsync(virtualFile, options.projectToClose, options.forceOpenInNewFrame)?.let {
      return it.orElse(null)
    }

    return withContext(Dispatchers.EDT) {
      processor.doOpenProject(virtualFile, options.projectToClose, options.forceOpenInNewFrame)
    }
  }

  @JvmStatic
  fun openProject(file: Path, options: OpenProjectTask): Project? {
    val fileAttributes = file.basicAttributesIfExists()
    if (fileAttributes == null) {
      Messages.showErrorDialog(IdeBundle.message("error.project.file.does.not.exist", file.toString()), CommonBundle.getErrorTitle())
      return null
    }
    val existing = findAndFocusExistingProjectForPath(file)
    if (existing != null) {
      return existing
    }
    if (isRemotePath(file.toString()) && !RecentProjectsManager.getInstance().hasPath(FileUtil.toSystemIndependentName(file.toString()))) {
      if (!confirmLoadingFromRemotePath(file.toString(), "warning.load.project.from.share", "title.load.project.from.share")) {
        return null
      }
    }
    if (fileAttributes.isDirectory) {
      val dir = file.resolve(Project.DIRECTORY_STORE_FOLDER)
      if (!Files.isDirectory(dir)) {
        Messages.showErrorDialog(IdeBundle.message("error.project.file.does.not.exist", dir.toString()), CommonBundle.getErrorTitle())
        return null
      }
    }
    try {
      return ProjectManagerEx.getInstanceEx().openProject(file, options)
    }
    catch (e: Exception) {
      Messages.showMessageDialog(IdeBundle.message("error.cannot.load.project", e.message),
                                 IdeBundle.message("title.cannot.load.project"), Messages.getErrorIcon())
    }
    return null
  }

  fun confirmLoadingFromRemotePath(path: String,
                                   msgKey: @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String,
                                   titleKey: @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String): Boolean {
    return showYesNoDialog(IdeBundle.message(msgKey, path), titleKey)
  }

  fun showYesNoDialog(message: @Nls String, titleKey: @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String): Boolean {
    return yesNo(IdeBundle.message(titleKey), message)
      .icon(Messages.getWarningIcon())
      .ask(getActiveFrameOrWelcomeScreen())
  }

  @JvmStatic
  fun getActiveFrameOrWelcomeScreen(): Window? {
    val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
    if (window != null) {
      return window
    }
    for (frame in Frame.getFrames()) {
      if (frame is IdeFrame && frame.isVisible) {
        return frame
      }
    }
    return null
  }

  fun isRemotePath(path: String): Boolean {
    return (path.contains("://") || path.contains("\\\\")) && !isWslUncPath(path)
  }

  fun findProject(file: Path): Project? = getOpenProjects().firstOrNull { isSameProject(file, it) }

  @JvmStatic
  fun findAndFocusExistingProjectForPath(file: Path): Project? {
    val project = findProject(file)
    if (project != null) {
      focusProjectWindow(project = project)
    }
    return project
  }

  /**
   * @return [GeneralSettings.OPEN_PROJECT_SAME_WINDOW] or
   * [GeneralSettings.OPEN_PROJECT_NEW_WINDOW] or
   * [GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH] or
   * `-1` (when a user cancels the dialog)
   */
  fun confirmOpenOrAttachProject(): Int {
    var mode = GeneralSettings.getInstance().confirmOpenNewProject
    if (mode == GeneralSettings.OPEN_PROJECT_ASK) {
      val exitCode = Messages.showDialog(
        IdeBundle.message("prompt.open.project.or.attach"),
        IdeBundle.message("prompt.open.project.or.attach.title"), arrayOf(
        IdeBundle.message("prompt.open.project.or.attach.button.this.window"),
        IdeBundle.message("prompt.open.project.or.attach.button.new.window"),
        IdeBundle.message("prompt.open.project.or.attach.button.attach"),
        CommonBundle.getCancelButtonText()
      ),
        0,
        Messages.getQuestionIcon(),
        ProjectNewWindowDoNotAskOption())
      mode = if (exitCode == 0) GeneralSettings.OPEN_PROJECT_SAME_WINDOW else if (exitCode == 1) GeneralSettings.OPEN_PROJECT_NEW_WINDOW else if (exitCode == 2) GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH else -1
      if (mode != -1) {
        LifecycleUsageTriggerCollector.onProjectFrameSelected(mode)
      }
    }
    return mode
  }

  @Deprecated("Use {@link #isSameProject(Path, Project)} ",
              ReplaceWith("projectFilePath != null && isSameProject(Path.of(projectFilePath), project)",
                          "com.intellij.ide.impl.ProjectUtil.isSameProject", "java.nio.file.Path"))
  fun isSameProject(projectFilePath: String?, project: Project): Boolean {
    return projectFilePath != null && isSameProject(Path.of(projectFilePath), project)
  }

  @JvmStatic
  fun isSameProject(projectFile: Path, project: Project): Boolean {
    val projectStore = project.stateStore
    val existingBaseDirPath = projectStore.projectBasePath
    if (existingBaseDirPath.fileSystem !== projectFile.fileSystem) {
      return false
    }

    if (Files.isDirectory(projectFile)) {
      return try {
        Files.isSameFile(projectFile, existingBaseDirPath)
      }
      catch (ignore: IOException) {
        false
      }
    }

    if (projectStore.storageScheme == StorageScheme.DEFAULT) {
      return try {
        Files.isSameFile(projectFile, projectStore.projectFilePath)
      }
      catch (ignore: IOException) {
        false
      }
    }

    var parent: Path? = projectFile.parent ?: return false
    val parentFileName = parent!!.fileName
    if (parentFileName != null && parentFileName.toString() == Project.DIRECTORY_STORE_FOLDER) {
      parent = parent.parent
      return parent != null && FileUtil.pathsEqual(parent.toString(), existingBaseDirPath.toString())
    }
    return projectFile.fileName.toString().endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION) &&
           FileUtil.pathsEqual(parent.toString(), existingBaseDirPath.toString())
  }

  /**
   * Focuses the specified project's window. If `stealFocusIfAppInactive` is `true` and corresponding logic is supported by OS
   * (making it work on Windows requires enabling focus stealing system-wise, see [com.intellij.ui.WinFocusStealer]), the window will
   * get the focus even if other application is currently active. Otherwise, there will be some indication that the target window requires
   * user attention. Focus stealing behaviour (enabled by `stealFocusIfAppInactive`) is generally not considered a proper application
   * behaviour, and should only be used in special cases, when we know that user definitely expects it.
   */
  @JvmStatic
  fun focusProjectWindow(project: Project?, stealFocusIfAppInactive: Boolean = false) {
    val frame = WindowManager.getInstance().getFrame(project) ?: return
    val appIsActive = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow != null

    // On macOS, `j.a.Window#toFront` restores the frame if needed.
    // On X Window, restoring minimized frame can steal focus from an active application, so we do it only when the IDE is active.
    if (SystemInfoRt.isWindows || (SystemInfoRt.isXWindow && appIsActive)) {
      val state = frame.extendedState
      if (state and Frame.ICONIFIED != 0) {
        frame.extendedState = state and Frame.ICONIFIED.inv()
      }
    }
    if (stealFocusIfAppInactive) {
      AppIcon.getInstance().requestFocus(frame as IdeFrame)
    }
    else {
      if (!SystemInfoRt.isXWindow || appIsActive) {
        // some Linux window managers allow `j.a.Window#toFront` to steal focus, so we don't call it on Linux when the IDE is inactive
        frame.toFront()
      }
      if (!SystemInfoRt.isWindows) {
        // on Windows, `j.a.Window#toFront` will request attention if needed
        AppIcon.getInstance().requestAttention(project, true)
      }
    }
  }

  @JvmStatic
  fun getBaseDir(): String {
    val defaultDirectory = GeneralSettings.getInstance().defaultProjectDirectory
    if (!defaultDirectory.isNullOrEmpty()) {
      return defaultDirectory.replace('/', File.separatorChar)
    }
    val lastProjectLocation = RecentProjectsManager.getInstance().lastProjectCreationLocation
    return lastProjectLocation?.replace('/', File.separatorChar) ?: getUserHomeProjectDir()
  }

  @JvmStatic
  fun getUserHomeProjectDir(): String {
    val productName = if (PlatformUtils.isCLion() || PlatformUtils.isAppCode() || PlatformUtils.isDataGrip()) {
      ApplicationNamesInfo.getInstance().productName
    }
    else {
      ApplicationNamesInfo.getInstance().lowercaseProductName
    }
    return SystemProperties.getUserHome().replace('/', File.separatorChar) + File.separator + productName + "Projects"
  }

  suspend fun openOrImportFilesAsync(list: List<Path>, location: String, projectToClose: Project? = null): Project? {
    for (file in list) {
      openOrImportAsync(file = file, options = OpenProjectTask {
        this.projectToClose = projectToClose
        forceOpenInNewFrame = true
      })?.let { return it }
    }

    var result: Project? = null
    for (file in list) {
      if (!Files.exists(file)) {
        continue
      }

      LOG.debug("$location: open file ", file)
      if (projectToClose == null) {
        val processor = CommandLineProjectOpenProcessor.getInstanceIfExists()
        if (processor != null) {
          val opened = processor.openProjectAndFile(file = file, tempProject = false)
          if (opened != null && result == null) {
            result = opened
          }
        }
      }
      else {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtilRt.toSystemIndependentName(file.toString()))
        if (virtualFile != null && virtualFile.isValid) {
          OpenFileAction.openFile(virtualFile, projectToClose)
        }
        result = projectToClose
      }
    }
    return result
  }

  //todo: merge somehow with getBaseDir
  @JvmStatic
  fun getProjectsPath(): @SystemDependent String {
    val application = ApplicationManager.getApplication()
    val fromSettings = if (application == null || application.isHeadlessEnvironment) null else GeneralSettings.getInstance().defaultProjectDirectory
    if (!fromSettings.isNullOrEmpty()) {
      return PathManager.getAbsolutePath(fromSettings)
    }

    if (ourProjectsPath == null) {
      val produceName = ApplicationNamesInfo.getInstance().productName.lowercase()
      val propertyName = String.format(PROPERTY_PROJECT_PATH, produceName)
      val propertyValue = System.getProperty(propertyName)
      ourProjectsPath = if (propertyValue != null) PathManager.getAbsolutePath(StringUtil.unquoteString(propertyValue, '\"'))
      else projectsDirDefault
    }
    return ourProjectsPath!!
  }

  private val projectsDirDefault: String
    get() = if (PlatformUtils.isDataGrip()) getUserHomeProjectDir() else PathManager.getConfigPath() + File.separator + PROJECTS_DIR

  fun getProjectPath(name: String): Path {
    return Path.of(getProjectsPath(), name)
  }

  fun getProjectFile(name: String): Path? {
    val projectDir = getProjectPath(name)
    return if (isProjectFile(projectDir)) projectDir else null
  }

  private fun isProjectFile(projectDir: Path): Boolean {
    return Files.isDirectory(projectDir.resolve(Project.DIRECTORY_STORE_FOLDER))
  }

  @JvmStatic
  fun openOrCreateProject(name: String, file: Path): Project? {
    return runBlockingUnderModalProgress {
      openOrCreateProjectInner(name, file)
    }
  }

  private suspend fun openOrCreateProjectInner(name: String, file: Path): Project? {
    val existingFile = if (isProjectFile(file)) file else null
    val projectManager = ProjectManagerEx.getInstanceEx()
    if (existingFile != null) {
      val openProjects = ProjectManager.getInstance().openProjects
      for (p in openProjects) {
        if (!p.isDefault && isSameProject(existingFile, p)) {
          focusProjectWindow(p, false)
          return p
        }
      }
      return projectManager.openProjectAsync(existingFile, OpenProjectTask { runConfigurators = true })
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    val created = try {
      withContext(Dispatchers.IO) {
        !Files.exists(file) && Files.createDirectories(file) != null || Files.isDirectory(file)
      }
    }
    catch (e: IOException) {
      false
    }

    var projectFile: Path? = null
    if (created) {
      val options = OpenProjectTask {
        isNewProject = true
        runConfigurators = true
        projectName = name
      }

      val project = projectManager.newProjectAsync(file = file, options = options)
      runInAutoSaveDisabledMode {
        saveSettings(componentManager = project, forceSavingAllSettings = true)
      }
      writeAction {
        Disposer.dispose(project)
      }
      projectFile = file
    }

    if (projectFile == null) {
      return null
    }

    return projectManager.openProjectAsync(projectStoreBaseDir = projectFile, options = OpenProjectTask {
      runConfigurators = true
      isProjectCreatedWithWizard = true
      isRefreshVfsNeeded = false
    })
  }

  @JvmStatic
  fun getRootFrameForWindow(window: Window?): IdeFrame? {
    var w = window ?: return null
    while (w.owner != null) {
      w = w.owner
    }
    return w as? IdeFrame
  }

  fun getProjectForWindow(window: Window?): Project? {
    return getRootFrameForWindow(window)?.project
  }

  @JvmStatic
  fun getProjectForComponent(component: Component?): Project? = getProjectForWindow(ComponentUtil.getWindow(component))

  @JvmStatic
  fun getActiveProject(): Project? = getProjectForWindow(KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow)

  interface ProjectCreatedCallback {
    fun projectCreated(project: Project?)
  }

  @JvmStatic
  fun getOpenProjects(): Array<Project> = ProjectUtilCore.getOpenProjects()

  @Internal
  @VisibleForTesting
  suspend fun openExistingDir(file: Path, currentProject: Project?): Project? {
    val canAttach = ProjectAttachProcessor.canAttachToProject()
    val preferAttach = currentProject != null &&
                       canAttach &&
                       (PlatformUtils.isDataGrip() && !ProjectUtilCore.isValidProjectPath(file) || PlatformUtils.isDataSpell())
    if (preferAttach && attachToProject(currentProject!!, file, null)) {
      return null
    }

    val project = if (canAttach) {
      val options = createOptionsToOpenDotIdeaOrCreateNewIfNotExists(file, currentProject)
      ProjectManagerEx.getInstanceEx().openProjectAsync(file, options)
    }
    else {
      openOrImportAsync(file, OpenProjectTask().withProjectToClose(currentProject))
    }
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      FileChooserUtil.setLastOpenedFile(project, file)
    }
    return project
  }

  @JvmStatic
  fun isValidProjectPath(file: Path): Boolean {
    return ProjectUtilCore.isValidProjectPath(file)
  }
}

private val delegateToCoroutineOnlyRunBlocking: Boolean =
  System.getProperty("ide.use.coroutine.only.runBlocking", "true").toBoolean()

@Suppress("DeprecatedCallableAddReplaceWith")
@Internal
@ScheduledForRemoval
@Deprecated(
  "Use runBlockingModal on EDT with proper owner and title, " +
  "or runBlockingCancellable(+withBackgroundProgressIndicator with proper title) on BGT"
)
// inline is not used - easier debug
fun <T> runUnderModalProgressIfIsEdt(task: suspend CoroutineScope.() -> T): T {
  if (!ApplicationManager.getApplication().isDispatchThread) {
    if (delegateToCoroutineOnlyRunBlocking) {
      return runBlockingMaybeCancellable(task)
    }
    return runBlocking(CoreProgressManager.getCurrentThreadProgressModality().asContextElement()) { task() }
  }
  return runBlockingUnderModalProgress(task = task)
}

@Suppress("DeprecatedCallableAddReplaceWith")
@Internal
@RequiresEdt
@ScheduledForRemoval
@Deprecated("Use runBlockingModal with proper owner and title")
fun <T> runBlockingUnderModalProgress(@NlsContexts.ProgressTitle title: String = "", project: Project? = null, task: suspend CoroutineScope.() -> T): T {
  if (delegateToCoroutineOnlyRunBlocking) {
    val owner = if (project == null) ModalTaskOwner.guess() else ModalTaskOwner.project(project)
    return runBlockingModal(owner, title, TaskCancellation.cancellable(), task)
  }
  return ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
    val modalityState = CoreProgressManager.getCurrentThreadProgressModality()
    runBlocking(modalityState.asContextElement()) {
      task()
    }
  }, title, true, project)
}

@Suppress("DeprecatedCallableAddReplaceWith")
@Internal
@Deprecated(message = "temporary solution for old code in java", level = DeprecationLevel.ERROR)
fun Project.executeOnPooledThread(task: Runnable) {
  coroutineScope.launch { task.run() }
}

@Suppress("DeprecatedCallableAddReplaceWith")
@Internal
@Deprecated(message = "temporary solution for old code in java", level = DeprecationLevel.ERROR)
fun <T> Project.computeOnPooledThread(task: Callable<T>): CompletableFuture<T> {
  return coroutineScope.async { task.call() }.asCompletableFuture()
}

@Suppress("DeprecatedCallableAddReplaceWith")
@Internal
@Deprecated(message = "temporary solution for old code in java", level = DeprecationLevel.ERROR)
fun Project.executeOnPooledIoThread(task: Runnable) {
  coroutineScope.launch(Dispatchers.IO) { task.run() }
}