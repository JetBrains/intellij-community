package com.intellij.platform.ide.nonModalWelcomeScreen.newProjectNotification

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider.Companion.getWelcomeScreenProjectPath
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider.Companion.isWelcomeScreenProject
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.createOptionsToOpenDotIdeaOrCreateNewIfNotExists
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenAppScopeHolder
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import javax.swing.JComponent

@Service(Service.Level.APP)
private class WelcomeScreenProjectCloseHandler {
  private val filesToCloseOnProjectClose = ConcurrentHashMap.newKeySet<VirtualFile>()

  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosingBeforeSave(project: Project) {
        if (isWelcomeScreenProject(project)) {
          val fileEditorManager = FileEditorManager.getInstance(project)
          filesToCloseOnProjectClose.forEach { file ->
            fileEditorManager.closeFile(file)
          }
          filesToCloseOnProjectClose.clear()
        }
      }
    })
  }

  fun addFileToCloseOnProjectClose(file: VirtualFile) {
    filesToCloseOnProjectClose.add(file)
  }
}

internal enum class ProjectRootResult(val openingStrategy: WelcomeScreenSingleFileOpeningCollector.OpeningStrategy?) {
  EXISTING_PROJECT(WelcomeScreenSingleFileOpeningCollector.OpeningStrategy.PROJECT),
  NEW_PROJECT(WelcomeScreenSingleFileOpeningCollector.OpeningStrategy.FOLDER),
  SUPPRESS_NOTIFICATION(null);

  fun asOpeningStrategy(fn: (WelcomeScreenSingleFileOpeningCollector.OpeningStrategy) -> Unit = {}): Unit? = openingStrategy?.let(fn)
}

internal data class ProjectRootInfo(
  val result: ProjectRootResult,
  val directory: VirtualFile?
)

/**
 * Shows a notification banner when a file is opened from the Welcome panel, suggesting to open or create a project.
 */
@ApiStatus.Internal
abstract class WelcomeScreenOpenFileNotificationProvider : EditorNotificationProvider, DumbAware {
  private val PARENT_TRAVERSAL_LIMIT = 10
  private val closedNotificationFiles = mutableSetOf<String>()

  // suppress notification when we are inside technical folders
  private val technicalFolderNames = setOf("out", "bin", "target", "build", "pkg", "vendor", "node_modules", "tmp", "dist",
                                           "coverage", ".git", ".cache", ".venv", ".gradle", ".idea")

  // avoid creating a project in these paths
  protected open val restrictedPaths: Set<Path> by lazy {
    val home = System.getProperty("user.home")
    val knownBigFolderPaths = setOf("Desktop", "Documents", "Downloads")
    val homePath = Paths.get(home)
    buildSet {
      add(homePath)
      val welcomeScreenProjectPath = getWelcomeScreenProjectPath()
      if (welcomeScreenProjectPath != null) {
        add(welcomeScreenProjectPath)
      }
      addAll(knownBigFolderPaths.map { homePath.resolve(it) })
    }
  }

  override fun collectNotificationData(
    project: Project,
    file: VirtualFile,
  ): Function<FileEditor, JComponent?>? {
    if (!isWelcomeScreenProject(project)) return null
    if (file is LightVirtualFile ||
        file.path in closedNotificationFiles ||
        ScratchUtil.isScratch(file) ||
        file.extension == "http"
    ) return null

    // Pre-compute the project root in the background thread
    val projectRootInfo = selectProjectRoot(file)

    if (projectRootInfo.result == ProjectRootResult.SUPPRESS_NOTIFICATION) {
      WelcomeScreenSingleFileOpeningCollector.logNotificationSuppressed()

      return null
    }

    projectRootInfo.result.asOpeningStrategy { WelcomeScreenSingleFileOpeningCollector.logNotificationShown(it) }

    return Function { fileEditor -> createNotificationPanel(project, file, fileEditor, projectRootInfo) }
  }

  private fun createNotificationPanel(
    project: Project,
    file: VirtualFile,
    fileEditor: FileEditor,
    projectRootInfo: ProjectRootInfo,
  ): EditorNotificationPanel {
    val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info)
    val directory = projectRootInfo.directory!!
    val isNewProject = projectRootInfo.result == ProjectRootResult.NEW_PROJECT

    panel.text = if (isNewProject) {
      NonModalWelcomeScreenBundle.message("welcome.screen.notification.open.file.in.folder.text", directory.name)
    } else {
      NonModalWelcomeScreenBundle.message("welcome.screen.notification.open.file.in.project.text", directory.name)
    }

    val buttonLabelText = if (isNewProject) {
      NonModalWelcomeScreenBundle.message("welcome.screen.notification.open.file.in.folder.button")
    } else {
      NonModalWelcomeScreenBundle.message("welcome.screen.notification.open.file.in.project.button")
    }

    panel.createActionLabel(buttonLabelText) {
      projectRootInfo.result.asOpeningStrategy { WelcomeScreenSingleFileOpeningCollector.logNotificationOpenButtonClicked(it) }

      service<WelcomeScreenProjectCloseHandler>().addFileToCloseOnProjectClose(file)
      WelcomeScreenAppScopeHolder.getInstance().coroutineScope.launch {
        val path = directory.toNioPath()
        val options = createOptionsToOpenDotIdeaOrCreateNewIfNotExists(path, project).copy(
          forceOpenInNewFrame = false,
          projectRootDir = path,
        )
        ProjectManagerEx.getInstanceEx().openProjectAsync(path, options)?.also {
          focusOnFile(it, file)
        }
      }
    }
    panel.setCloseAction {
      projectRootInfo.result.asOpeningStrategy { WelcomeScreenSingleFileOpeningCollector.logNotificationClosed(it) }

      closedNotificationFiles.add(file.path)
      EditorNotifications.getInstance(project).updateNotifications(file)
    }
    return panel
  }

  private suspend fun focusOnFile(project: Project, virtualFile: VirtualFile) {
    withContext(Dispatchers.EDT) {
      FileEditorManager.getInstance(project).openFile(virtualFile, true)
      ProjectView.getInstance(project).select(null, virtualFile, true)
    }
  }

  @RequiresBackgroundThread
  private fun selectProjectRoot(virtualFile: VirtualFile): ProjectRootInfo {
    val directParent = virtualFile.parent
                       ?: return ProjectRootInfo(ProjectRootResult.SUPPRESS_NOTIFICATION, null)

    if (technicalFolderNames.contains(directParent.name)
        || restrictedPaths.any { pathsEqual(it, Paths.get(directParent.path)) }) {
      return ProjectRootInfo(ProjectRootResult.SUPPRESS_NOTIFICATION, null)
    }

    return try {
      var current: VirtualFile? = directParent
      var steps = 0
      while (current != null && steps < PARENT_TRAVERSAL_LIMIT) {
        // Suppress if inside a technical folder
        if (technicalFolderNames.contains(current.name)) {
          return ProjectRootInfo(ProjectRootResult.SUPPRESS_NOTIFICATION, null)
        }
        // Stop traversing if reached restricted path or root
        if (current.parent == null || restrictedPaths.any { pathsEqual(it, Paths.get(current.path)) }) {
          break
        }
        // If a known project directory is found before hitting restrictions, use it
        if (isKnownProjectDirectory(current)) {
          return ProjectRootInfo(ProjectRootResult.EXISTING_PROJECT, current)
        }
        current = current.parent
        steps++
      }
      ProjectRootInfo(ProjectRootResult.NEW_PROJECT, directParent)
    }
    catch (_: IOException) {
      ProjectRootInfo(ProjectRootResult.NEW_PROJECT, directParent)
    }
  }

  protected open fun isKnownProjectDirectory(file: VirtualFile): Boolean {
    val path = Paths.get(file.path)
    return Files.exists(path.resolve(Project.DIRECTORY_STORE_FOLDER))
  }
}

private fun pathsEqual(path1: Path, path2: Path): Boolean {
  val normalized1 = path1.normalize()
  val normalized2 = path2.normalize()
  return normalized1.toString().equals(normalized2.toString(), ignoreCase = !SystemInfo.isFileSystemCaseSensitive)
}
