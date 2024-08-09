// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.intellij.ide.impl.NewProjectUtil.setCompilerOutputPath
import com.intellij.ide.impl.ProjectViewSelectInTarget
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.ide.util.importProject.JavaModuleInsight
import com.intellij.ide.util.importProject.LibrariesDetectionStep
import com.intellij.ide.util.importProject.RootDetectionProcessor
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl
import com.intellij.notification.*
import com.intellij.notification.impl.NotificationIdsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.roots.ui.configuration.lookupAndSetupSdkBlocking
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.*
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.isOpenedByPlatformProcessor
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.util.SystemProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.event.HyperlinkEvent

private val NOTIFICATION_GROUP: NotificationGroup
  get() = NotificationGroupManager.getInstance().getNotificationGroup("Build Script Found")

private const val SCAN_DEPTH_LIMIT = 5
private const val MAX_ROOTS_IN_TRIVIAL_PROJECT_STRUCTURE = 3

private class SetupJavaProjectFromSourcesActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (SystemProperties.getBooleanProperty("idea.java.project.setup.disabled", false)) {
      return
    }

    if (!project.isOpenedByPlatformProcessor()) {
      return
    }

    val projectDir = project.baseDir ?: return

    // todo get current project structure, and later setup from sources only if it wasn't manually changed by the user

    val title = JavaUiBundle.message("task.searching.for.project.sources")

    withBackgroundProgress(project, title) {
      val importers = searchImporters(projectDir)
      if (!importers.isEmpty) {
        withContext(Dispatchers.EDT) {
          setCompilerOutputPath(project, "${projectDir.path}/out")
        }
                
        blockingContext {
          showNotificationToImport(project, projectDir, importers)
        }
      }
      else {
        setupFromSources(project = project, projectDir = projectDir)
      }
    }
  }
}

private fun searchImporters(projectDirectory: VirtualFile): ArrayListMultimap<ProjectOpenProcessor, VirtualFile> {
  val providersAndFiles = ArrayListMultimap.create<ProjectOpenProcessor, VirtualFile>()
  VfsUtil.visitChildrenRecursively(projectDirectory, object : VirtualFileVisitor<Void>(NO_FOLLOW_SYMLINKS, limit(SCAN_DEPTH_LIMIT)) {
    override fun visitFileEx(file: VirtualFile): Result {
      if (file.isDirectory && FileTypeRegistry.getInstance().isFileIgnored(file)) {
        return SKIP_CHILDREN
      }

      val providers = ProjectOpenProcessor.EXTENSION_POINT_NAME.extensionList.filter { provider ->
        provider.canOpenProject(file) &&
        provider !is PlatformProjectOpenProcessor
      }

      for (provider in providers) {
        val files = providersAndFiles.get(provider)
        if (files.isEmpty()) {
          files.add(file)
        }
        else if (!VfsUtilCore.isAncestor(files.last(), file, true)) { // add only top-level file/folders for each of providers
          files.add(file)
        }
      }
      return CONTINUE
    }
  })
  return providersAndFiles
}

@Service(Service.Level.PROJECT)
private class CoroutineScopeService(val coroutineScope: CoroutineScope)

private fun showNotificationToImport(project: Project,
                                     projectDirectory: VirtualFile,
                                     providersAndFiles: ArrayListMultimap<ProjectOpenProcessor, VirtualFile>) {
  val showFileInProjectViewListener = object : NotificationListener.Adapter() {
    override fun hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
      val file = LocalFileSystem.getInstance().findFileByPath(e.description)
      ProjectViewSelectInTarget.select(project, file, ProjectViewPane.ID, null, file, true)
    }
  }

  val title: String
  val content: String
  if (providersAndFiles.keySet().size == 1) {
    val processor = providersAndFiles.keySet().single()
    val files = providersAndFiles[processor]
    title = JavaUiBundle.message("build.script.found.notification", processor.name, files.size)
    content = filesToLinks(files, projectDirectory)
  }
  else {
    title = JavaUiBundle.message("build.scripts.from.multiple.providers.found.notification")
    content = formatContent(providersAndFiles, projectDirectory)
  }

  val notification = NOTIFICATION_GROUP.createNotification(title, content, NotificationType.INFORMATION)
    .setSuggestionType(true)
    .setDisplayId(SCRIPT_FOUND_NOTIFICATION)
    .setListener(showFileInProjectViewListener)

  if (providersAndFiles.keySet().all { it.canImportProjectAfterwards() }) {
    val actionName = JavaUiBundle.message("build.script.found.notification.import", providersAndFiles.keySet().size)
    notification.addAction(NotificationAction.createSimpleExpiring(actionName) {
      val cs = project.service<CoroutineScopeService>().coroutineScope
      cs.launch {
        for ((provider, files) in providersAndFiles.asMap()) {
          for (file in files) {
            provider.importProjectAfterwardsAsync(project, file)
          }
        }
      }
    })
  }

  notification.notify(project)
}


@NlsSafe
private fun formatContent(providersAndFiles: Multimap<ProjectOpenProcessor, VirtualFile>,
                          projectDirectory: VirtualFile): String {
  return providersAndFiles.asMap().entries.joinToString("<br/>") { (provider, files) ->
    provider.name + ": " + filesToLinks(files, projectDirectory)
  }
}

@NlsSafe
private fun filesToLinks(files: MutableCollection<VirtualFile>, projectDirectory: VirtualFile): String {
  return files.joinToString { file -> "<a href='${file.path}'>${VfsUtil.getRelativePath(file, projectDirectory)}</a>" }
}

private suspend fun setupFromSources(project: Project, projectDir: VirtualFile) {
  val builder = ProjectFromSourcesBuilderImpl(WizardContext(project, project), ModulesProvider.EMPTY_MODULES_PROVIDER)
  val projectPath = projectDir.path
  builder.baseProjectPath = projectPath
  val roots = RootDetectionProcessor.detectRoots(File(projectPath))
  val rootsMap = RootDetectionProcessor.createRootsMap(roots)
  builder.setupProjectStructure(rootsMap)
  for (detector in rootsMap.keySet()) {
    val descriptor = builder.getProjectDescriptor(detector)

    val moduleInsight = JavaModuleInsight(DelegatingProgressIndicator(), builder.existingModuleNames, builder.existingProjectLibraryNames)
    descriptor.libraries = LibrariesDetectionStep.calculate(moduleInsight, builder)

    moduleInsight.scanModules()
    descriptor.modules = moduleInsight.suggestedModules
  }

  withContext(Dispatchers.EDT) {
    writeIntentReadAction {
      builder.commit(project)

      val compileOutput = if (projectPath.endsWith('/')) "${projectPath}out" else "$projectPath/out"
      setCompilerOutputPath(project, compileOutput)
    }
  }

  val modules = ModuleManager.getInstance(project).modules
  if (modules.any { ModuleType.get(it) is JavaModuleType }) {
    coroutineToIndicator {
      lookupAndSetupSdkBlocking(project, ProgressManager.getGlobalProgressIndicator(), JavaSdk.getInstance()) {
        JavaSdkUtil.applyJdkToProject(project, it)
      }
    }
  }

  if (roots.size > MAX_ROOTS_IN_TRIVIAL_PROJECT_STRUCTURE) {
    notifyAboutAutomaticProjectStructure(project)
  }
}

private fun notifyAboutAutomaticProjectStructure(project: Project) {
  NOTIFICATION_GROUP.createNotification(JavaUiBundle.message("project.structure.automatically.detected.notification"),
                                        NotificationType.INFORMATION)
    .setDisplayId(STRUCTURE_DETECTED_NOTIFICATION)
    .addAction(NotificationAction.createSimpleExpiring(
      JavaUiBundle.message("project.structure.automatically.detected.notification.gotit.action")) {})
    .addAction(NotificationAction.createSimpleExpiring(
      JavaUiBundle.message("project.structure.automatically.detected.notification.configure.action")) {
      ProjectSettingsService.getInstance(project).openProjectSettings()
    })
    .notify(project)
}

const val STRUCTURE_DETECTED_NOTIFICATION = "project.structure.automatically.detected.notification.id"
const val SCRIPT_FOUND_NOTIFICATION = "build.script.found.notification.id"

class SetupJavaProjectFromSourcesNotificationIds : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(SCRIPT_FOUND_NOTIFICATION, STRUCTURE_DETECTED_NOTIFICATION)
}
