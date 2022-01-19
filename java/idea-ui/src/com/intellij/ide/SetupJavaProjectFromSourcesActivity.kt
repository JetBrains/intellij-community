// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.roots.ui.configuration.findAndSetupSdk
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.*
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.isOpenedByPlatformProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.util.SystemProperties
import java.io.File
import javax.swing.event.HyperlinkEvent

private val NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Build Script Found")
private val SETUP_JAVA_PROJECT_IS_DISABLED = SystemProperties.getBooleanProperty("idea.java.project.setup.disabled", false)

private const val SCAN_DEPTH_LIMIT = 5
private const val MAX_ROOTS_IN_TRIVIAL_PROJECT_STRUCTURE = 3
private val LOG = logger<SetupJavaProjectFromSourcesActivity>()

internal class SetupJavaProjectFromSourcesActivity : StartupActivity {
  override fun runActivity(project: Project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment || SETUP_JAVA_PROJECT_IS_DISABLED) {
      return
    }
    if (!project.isOpenedByPlatformProcessor()) {
      return
    }

    // todo get current project structure, and later setup from sources only if it wasn't manually changed by the user

    val title = JavaUiBundle.message("task.searching.for.project.sources")
    ProgressManager.getInstance().run(object: Task.Backgroundable(project, title, true) {
      override fun run(indicator: ProgressIndicator) {
        val projectDir = project.baseDir
        val importers = searchImporters(projectDir)
        if (!importers.isEmpty) {
          showNotificationToImport(project, projectDir, importers)
        }
        else {
          setupFromSources(project, projectDir, indicator)
        }
      }
    })
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

    val notification = NOTIFICATION_GROUP.createNotification(title, content, NotificationType.INFORMATION).setListener(showFileInProjectViewListener)

    if (providersAndFiles.keySet().all { it.canImportProjectAfterwards() }) {
      val actionName = JavaUiBundle.message("build.script.found.notification.import", providersAndFiles.keySet().size)
      notification.addAction(NotificationAction.createSimpleExpiring(actionName) {
        for ((provider, files) in providersAndFiles.asMap()) {
          for (file in files) {
            provider.importProjectAfterwards(project, file)
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
  private fun filesToLinks(files: MutableCollection<VirtualFile>, projectDirectory: VirtualFile) =
    files.joinToString { file ->
      "<a href='${file.path}'>${VfsUtil.getRelativePath(file, projectDirectory)}</a>"
  }

  private fun setupFromSources(project: Project,
                               projectDir: VirtualFile,
                               indicator: ProgressIndicator) {
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

    ApplicationManager.getApplication().invokeAndWait {
      builder.commit(project)

      val compileOutput = if (projectPath.endsWith('/')) "${projectPath}out" else "$projectPath/out"
      setCompilerOutputPath(project, compileOutput)
    }

    val modules = ModuleManager.getInstance(project).modules
    if (modules.any { it is JavaModuleType }) {
      findAndSetupSdk(project, indicator, JavaSdk.getInstance()) {
        JavaSdkUtil.applyJdkToProject(project, it)
      }
    }

    if (roots.size > MAX_ROOTS_IN_TRIVIAL_PROJECT_STRUCTURE) {
      notifyAboutAutomaticProjectStructure(project)
    }
  }

  private fun notifyAboutAutomaticProjectStructure(project: Project) {
    NOTIFICATION_GROUP.createNotification(JavaUiBundle.message("project.structure.automatically.detected.notification"), NotificationType.INFORMATION)
      .addAction(NotificationAction.createSimpleExpiring(JavaUiBundle.message("project.structure.automatically.detected.notification.gotit.action")) {})
      .addAction(NotificationAction.createSimpleExpiring(JavaUiBundle.message("project.structure.automatically.detected.notification.configure.action")) {
        ProjectSettingsService.getInstance(project).openProjectSettings()
      })
      .notify(project)
  }
}
