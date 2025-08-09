// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.CommonBundle
import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.configurationStore.saveSettings
import com.intellij.execution.wsl.WslPath.Companion.isWslUncPath
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.GeneralLocalSettings
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.actions.OpenFileAction
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.application.*
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectStorePathManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
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
import com.intellij.platform.PROJECT_OPENED_BY_PLATFORM_PROCESSOR
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.createOptionsToOpenDotIdeaOrCreateNewIfNotExists
import com.intellij.platform.attachToProjectAsync
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.project.stateStore
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.ui.AppIcon
import com.intellij.ui.ComponentUtil
import com.intellij.ui.DisposableWindow
import com.intellij.util.ModalityUiUtil
import com.intellij.util.PathUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlin.Result
import kotlin.getOrThrow

private val LOG = Logger.getInstance(ProjectUtil::class.java)
private var ourProjectPath: String? = null

object ProjectUtil {
  private const val PROJECTS_DIR = "projects"
  private const val PROPERTY_PROJECT_PATH = "%s.project.path"

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
              ReplaceWith("ProjectManager.getInstance().closeAndDispose(project)", "com.intellij.openapi.project.ProjectManager"),
              level = DeprecationLevel.ERROR)
  @JvmStatic
  fun closeAndDispose(project: Project): Boolean {
    return ProjectManager.getInstance().closeAndDispose(project)
  }

  @JvmStatic
  fun openOrImport(path: Path, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    return openOrImport(path, OpenProjectTask {
      this.projectToClose = projectToClose
      this.forceOpenInNewFrame = forceOpenInNewFrame
    })
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
    return runUnderModalProgressIfIsEdt {
      openOrImportAsync(Path.of(path), OpenProjectTask {
        this.projectToClose = projectToClose
        this.forceOpenInNewFrame = forceOpenInNewFrame
      })
    }
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
    for (provider in ProjectOpenProcessor.EXTENSION_POINT_NAME.lazySequence()) {
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
    if (isValidProjectPath(file)) {
      // see OpenProjectTest.`open valid existing project dir with inability to attach using OpenFileAction` test about why `runConfigurators = true` is specified here
      return (serviceAsync<ProjectManager>() as ProjectManagerEx).openProjectAsync(file, options.copy(runConfigurators = true))
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
      catch (_: IOException) {
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
      LOG.info("No processor found for project in $file")
      return null
    }

    val project: Project?
    if (processors.size == 1 && processors[0] is PlatformProjectOpenProcessor) {
      project = (serviceAsync<ProjectManager>() as ProjectManagerEx).openProjectAsync(
        projectStoreBaseDir = file,
        options = options.copy(
          isNewProject = true,
          useDefaultProjectAsTemplate = true,
          runConfigurators = true,
          beforeOpen = {
            it.putUserData(PROJECT_OPENED_BY_PLATFORM_PROCESSOR, true)
            options.beforeOpen?.invoke(it) ?: true
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
    ProjectOpenProcessor.EXTENSION_POINT_NAME.forEachExtensionSafe { processor ->
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
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal(), project.disposed) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
        toolWindow?.activate(null)
      }
    }
    return project
  }

  fun openProject(path: String, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    return openProject(file = Path.of(path),
                       options = OpenProjectTask {
                         this.projectToClose = projectToClose
                         this.forceOpenInNewFrame = forceOpenInNewFrame
                       })
  }

  private suspend fun chooseProcessorAndOpenAsync(
    processors: MutableList<ProjectOpenProcessor>,
    virtualFile: VirtualFile,
    options: OpenProjectTask,
  ): Project? {
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

    LOG.info("Using processor ${processor.name} to open the project at ${virtualFile.path}")

    try {
      return processor.openProjectAsync(virtualFile, options.projectToClose, options.forceOpenInNewFrame)
    }
    catch (e: UnsupportedOperationException) {
      if (e != ProjectOpenProcessor.unimplementedOpenAsync) {
        throw e
      }
    }

    return withContext(Dispatchers.EDT) {
      //readaction is not enough
      writeIntentReadAction {
        processor.doOpenProject(virtualFile, options.projectToClose, options.forceOpenInNewFrame)
      }
    }
  }

  @JvmStatic
  fun openProject(file: Path, options: OpenProjectTask): Project? {
    val fileAttributes = file.basicAttributesIfExists()
    if (fileAttributes == null) {
      Messages.showErrorDialog(IdeBundle.message("error.project.file.does.not.exist", file.toString()), CommonBundle.getErrorTitle())
      return null
    }

    findAndFocusExistingProjectForPath(file)?.let {
      return it
    }

    if (isRemotePath(file.toString()) && !RecentProjectsManager.getInstance().hasPath(FileUtil.toSystemIndependentName(file.toString()))) {
      if (!confirmLoadingFromRemotePath(file.toString(), "warning.load.project.from.share", "title.load.project.from.share")) {
        return null
      }
    }
    if (fileAttributes.isDirectory) {
      val storePathManager = ProjectStorePathManager.getInstance()
      val isKnownProject = storePathManager.testStoreDirectoryExistsForProjectRoot(file)
      if (!isKnownProject) {
        val dirPath = storePathManager.getStoreDirectoryPath(file)
        Messages.showErrorDialog(IdeBundle.message("error.project.file.does.not.exist", dirPath.toString()), CommonBundle.getErrorTitle())
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

  fun confirmLoadingFromRemotePath(
    path: String,
    msgKey: @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String,
    titleKey: @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String,
  ): Boolean {
    return showYesNoDialog(IdeBundle.message(msgKey, path), titleKey)
  }

  fun showYesNoDialog(message: @Nls String, titleKey: @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String): Boolean {
    return MessageDialogBuilder
      .yesNo(IdeBundle.message(titleKey), message)
      .icon(Messages.getWarningIcon())
      .ask(getActiveFrameOrWelcomeScreen())
  }

  @JvmStatic
  fun getActiveFrameOrWelcomeScreen(): Window? {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow?.let {
      return it
    }
    return Frame.getFrames().firstOrNull { it is IdeFrame && it.isVisible }
  }

  fun isRemotePath(path: String): Boolean {
    return (path.contains("://") || path.contains("\\\\")) && !isWslUncPath(path)
  }

  fun findProject(file: Path): Project? = getOpenProjects().firstOrNull { isSameProject(file, it) }

  @JvmStatic
  fun findAndFocusExistingProjectForPath(file: Path): Project? {
    val project = findProject(file) ?: return null
    focusProjectWindow(project = project)
    return project
  }

  /**
   * @return [GeneralSettings.OPEN_PROJECT_SAME_WINDOW] or
   * [GeneralSettings.OPEN_PROJECT_NEW_WINDOW] or
   * [GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH] or
   * `-1` (when a user cancels the dialog)
   */
  @JvmOverloads
  fun confirmOpenOrAttachProject(project: Project? = null, computed: ProjectAttachProcessor? = null): Int {
    var mode = GeneralSettings.getInstance().confirmOpenNewProject
    if (mode == GeneralSettings.OPEN_PROJECT_ASK) {
      if (ApplicationManager.getApplication().isUnitTestMode) return GeneralSettings.OPEN_PROJECT_NEW_WINDOW
      val processor = computed ?: ProjectAttachProcessor.getProcessor(project, null, null)
      val exitCode = Messages.showDialog(
        project?.let { processor?.getDescription(it) } ?: IdeBundle.message("prompt.open.project.or.attach"),
        IdeBundle.message("prompt.open.project.or.attach.title"), arrayOf(
        IdeBundle.message("prompt.open.project.or.attach.button.this.window"),
        IdeBundle.message("prompt.open.project.or.attach.button.new.window"),
        project?.let { processor?.getActionText(it) } ?: IdeBundle.message("prompt.open.project.or.attach.button.attach"),
        CommonBundle.getCancelButtonText()
      ),
        processor?.defaultOptionIndex(project) ?: 0,
        Messages.getQuestionIcon(),
        ProjectNewWindowDoNotAskOption())
      mode = if (exitCode == 0) GeneralSettings.OPEN_PROJECT_SAME_WINDOW else if (exitCode == 1) GeneralSettings.OPEN_PROJECT_NEW_WINDOW else if (exitCode == 2) GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH else -1
      if (mode != -1) {
        LifecycleUsageTriggerCollector.onProjectFrameSelected(mode)
      }
    }
    return mode
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
      catch (_: IOException) {
        false
      }
    }

    if (projectStore.storageScheme == StorageScheme.DEFAULT) {
      return try {
        Files.isSameFile(projectFile, projectStore.projectFilePath)
      }
      catch (_: IOException) {
        false
      }
    }

    val storeDir = projectStore.directoryStorePath ?: return false
    if (projectFile.startsWith(storeDir)) {
      return true
    }
    val parent = projectFile.parent ?: return false
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
  @RequiresEdt
  fun focusProjectWindow(project: Project?, stealFocusIfAppInactive: Boolean = false) {
    val frame = WindowManager.getInstance().getFrame(project) ?: return
    val appIsActive = getActiveWindow() != null

    // On macOS, `j.a.Window#toFront` restores the frame if needed.
    // On X Window, restoring minimized frame can steal focus from an active application, so we do it only when the IDE is active.
    if (SystemInfoRt.isWindows || (StartupUiUtil.isXToolkit() && appIsActive)) {
      val state = frame.extendedState
      if (state and Frame.ICONIFIED != 0) {
        frame.extendedState = state and Frame.ICONIFIED.inv()
      }
    }
    if (stealFocusIfAppInactive) {
      AppIcon.getInstance().requestFocus(frame as IdeFrame)
    }
    else {
      if (!StartupUiUtil.isXToolkit() || appIsActive) {
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
    val defaultDirectory = GeneralLocalSettings.getInstance().defaultProjectDirectory
    if (defaultDirectory.isNotEmpty()) {
      return defaultDirectory.replace('/', File.separatorChar)
    }
    val lastProjectLocation = RecentProjectsManager.getInstance().lastProjectCreationLocation
    return lastProjectLocation?.replace('/', File.separatorChar) ?: getUserHomeProjectDir()
  }

  @JvmStatic
  fun getUserHomeProjectDir(): String {
    val productName = if (PlatformUtils.isCLion() || PlatformUtils.isAppCode() || PlatformUtils.isDataGrip() || PlatformUtils.isMPS()) {
      ApplicationNamesInfo.getInstance().productName
    }
    else {
      ApplicationNamesInfo.getInstance().lowercaseProductName
    }
    return SystemProperties.getUserHome().replace('/', File.separatorChar) + File.separator + productName + "Projects"
  }

  suspend fun openOrImportFilesAsync(list: List<Path>, location: String, projectToClose: Project? = null): Project? {
    for (file in list) {
      FUSProjectHotStartUpMeasurer.withProjectContextElement(file) {
        openOrImportAsync(file = file, options = OpenProjectTask {
          this.projectToClose = projectToClose
          forceOpenInNewFrame = true
        })
      }?.also {
        return it
      }
    }

    var result: Project? = null
    for (file in list) {
      if (!Files.exists(file)) {
        continue
      }

      LOG.debug { "$location: open file $file" }
      if (projectToClose == null) {
        val processor = CommandLineProjectOpenProcessor.getInstanceIfExists()
        if (processor != null) {
          val opened = FUSProjectHotStartUpMeasurer.withProjectContextElement(file) {
            processor.openProjectAndFile(file = file, tempProject = false)
          }
          if (opened != null) {
            if (result == null) {
              result = opened
            }
            else {
              FUSProjectHotStartUpMeasurer.openingMultipleProjects(false, list.size, false)
            }
          }
        }
      }
      else {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtilRt.toSystemIndependentName(file.toString()))
        if (virtualFile != null && virtualFile.isValid) {
          withContext(Dispatchers.EDT) {
            //readaction is not enough
            writeIntentReadAction {
              OpenFileAction.openFile(virtualFile, projectToClose)
            }
          }
        }
        result = projectToClose
      }
    }
    return result
  }

  //todo: merge somehow with getBaseDir
  @JvmStatic
  fun getProjectPath(): @SystemDependent String {
    val application = ApplicationManager.getApplication()
    val fromSettings = if (application == null || application.isHeadlessEnvironment) null else GeneralLocalSettings.getInstance().defaultProjectDirectory
    if (!fromSettings.isNullOrEmpty()) {
      return PathManager.getAbsolutePath(fromSettings)
    }

    if (ourProjectPath == null) {
      val produceName = ApplicationNamesInfo.getInstance().productName.lowercase()
      val propertyName = String.format(PROPERTY_PROJECT_PATH, produceName)
      val propertyValue = System.getProperty(propertyName)
      ourProjectPath = if (propertyValue != null) {
        PathManager.getAbsolutePath(StringUtil.unquoteString(propertyValue, '\"'))
      }
      else {
        projectsDirDefault
      }
    }
    return ourProjectPath!!
  }

  private val projectsDirDefault: String
    get() = if (PlatformUtils.isDataGrip() || PlatformUtils.isDataSpell()) getUserHomeProjectDir() else PathManager.getConfigPath() + File.separator + PROJECTS_DIR

  fun getProjectPath(name: String): Path {
    return Path.of(getProjectPath(), name)
  }

  fun getProjectFile(name: String): Path? {
    val projectDir = getProjectPath(name)
    return if (service<ProjectStorePathManager>().testStoreDirectoryExistsForProjectRoot(projectDir)) projectDir else null
  }

  @JvmStatic
  @RequiresEdt
  fun openOrCreateProject(name: String, file: Path): Project? {
    return runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
      openOrCreateProjectInner(name, file)
    }
  }

  private suspend fun openOrCreateProjectInner(name: String, file: Path): Project? {
    val storePathManager = serviceAsync<ProjectStorePathManager>()
    val existingFile = if (storePathManager.testStoreDirectoryExistsForProjectRoot(file)) file else null
    val projectManager = serviceAsync<ProjectManager>() as ProjectManagerEx
    if (existingFile != null) {
      for (p in projectManager.openProjects) {
        if (isSameProject(existingFile, p)) {
          focusProjectWindow(project = p, stealFocusIfAppInactive = false)
          return p
        }
      }
      return projectManager.openProjectAsync(existingFile, OpenProjectTask { runConfigurators = true })
    }

    val created = try {
      withContext(Dispatchers.IO) {
        !Files.exists(file) && Files.createDirectories(file) != null || Files.isDirectory(file)
      }
    }
    catch (_: IOException) {
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
      edtWriteAction {
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

  @JvmStatic
  fun getProjectForWindow(window: Window?): Project? {
    return getRootFrameForWindow(window)?.project
  }

  @JvmStatic
  fun getProjectForComponent(component: Component?): Project? = getProjectForWindow(ComponentUtil.getWindow(component))

  @JvmStatic
  fun getActiveProject(): Project? = getProjectForWindow(getActiveWindow())

  @JvmStatic
  fun getOpenProjects(): Array<Project> = ProjectUtilCore.getOpenProjects()

  @Internal
  @VisibleForTesting
  suspend fun openExistingDir(file: Path, currentProject: Project?, forceReuseFrame: Boolean = false): Project? {
    val canAttach = ProjectAttachProcessor.canAttachToProject()
    val preferAttach = currentProject != null &&
                       canAttach &&
                       (PlatformUtils.isDataGrip() && !isValidProjectPath(file))
    if (preferAttach && attachToProjectAsync(projectToClose = currentProject, projectDir = file, callback = null)) {
      return null
    }

    val project = if (canAttach) {
      val options = createOptionsToOpenDotIdeaOrCreateNewIfNotExists(file, currentProject).copy(
        forceReuseFrame = forceReuseFrame
      )
      (serviceAsync<ProjectManager>() as ProjectManagerEx).openProjectAsync(file, options)
    }
    else {
      val options = OpenProjectTask().withProjectToClose(currentProject).copy(
        forceReuseFrame = forceReuseFrame
      )
      openOrImportAsync(file, options)
    }
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      FileChooserUtil.setLastOpenedFile(project, file)
    }
    return project
  }

  @Deprecated("Please use the suspending version to avoid FS access on an unintended thread", ReplaceWith("isValidProjectPath(file)"))
  @JvmStatic
  @JvmName("isValidProjectPath")
  fun isValidProjectPathBlocking(file: Path): Boolean {
    return ProjectUtilCore.isValidProjectPath(file)
  }

  @JvmName("isValidProjectPathAsync")
  @JvmStatic
  suspend fun isValidProjectPath(file: Path): Boolean {
    return withContext(Dispatchers.IO) {
      ProjectUtilCore.isValidProjectPath(file)
    }
  }
}

@Internal
@ScheduledForRemoval
@Deprecated(
  "Use runWithModalProgressBlocking on EDT with proper owner and title, " +
  "or runBlockingCancellable(+withBackgroundProgress with proper title) on BGT"
)
fun <T> runUnderModalProgressIfIsEdt(task: suspend CoroutineScope.() -> T): T {
  if (ApplicationManager.getApplication().isDispatchThread) {
    return runWithModalProgressBlocking(ModalTaskOwner.guess(), "", TaskCancellation.cancellable(), task)
  }
  else {
    return runBlockingMaybeCancellable(task)
  }
}

private fun getActiveWindow(): Window? {
  val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
  if (window is DisposableWindow && window.isWindowDisposed) return null
  return window
}
