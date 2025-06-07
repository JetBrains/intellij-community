// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.impl.NewProjectUtil
import com.intellij.ide.impl.ProjectUtil.findAndFocusExistingProjectForPath
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard
import com.intellij.ide.util.newProjectWizard.AddModuleWizard
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ui.configuration.actions.NewModuleAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.projectImport.DeprecatedProjectBuilderForImport
import com.intellij.projectImport.ProjectImportProvider
import java.awt.Component
import java.nio.file.Path
import java.util.function.Predicate

private const val LAST_IMPORTED_LOCATION = "last.imported.location"
private val LOG = logger<ImportModuleAction>()

open class ImportModuleAction : AnAction(), NewProjectOrModuleAction {

  override fun actionPerformed(e: AnActionEvent) {
    doImport(getEventProject(e))
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.isEnabled = getEventProject(e) != null
    NewProjectAction.updateActionText(this, e)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun getActionText(isInNewSubmenu: Boolean, isInJavaIde: Boolean): String {
    return JavaUiBundle.message("import.module.action.text", if (isInNewSubmenu) 1 else 0, if (isInJavaIde) 1 else 0)
  }

  override fun isDumbAware() = true

  companion object {

    @JvmStatic
    fun doImport(project: Project?): List<Module> {
      return doImport(project) {
        selectFileAndCreateWizard(project = project, dialogParent = null)
      }
    }

    @JvmStatic
    fun doImport(project: Project?, createWizard: () -> AbstractProjectWizard?): List<Module> {
      val wizard = createWizard() ?: return emptyList()
      try {
        if (wizard.stepCount > 0 && !wizard.showAndGet()) {
          return emptyList()
        }
        return doCreateFromWizard(project, wizard)
      }
      finally {
        Disposer.dispose(wizard.disposable)
      }
    }

    @JvmStatic
    fun createFromWizard(project: Project?, wizard: AbstractProjectWizard): List<Module> {
      try {
        return doCreateFromWizard(project, wizard)
      }
      finally {
        wizard.disposeIfNeeded()
      }
    }

    @JvmStatic
    fun selectFileAndCreateWizard(project: Project?, dialogParent: Component?): AddModuleWizard? {
      val descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
      descriptor.isHideIgnored = false
      descriptor.title = JavaUiBundle.message("chooser.title.select.file.or.directory.to.import")
      val providers = getProviders(project)
      val description = getFileChooserDescription(providers)
      descriptor.description = description
      return selectFileAndCreateWizard(project, dialogParent, descriptor, providers.toTypedArray())
    }

    @JvmStatic
    fun selectFileAndCreateWizard(
      project: Project?,
      dialogParent: Component?,
      descriptor: FileChooserDescriptor,
      providers: Array<ProjectImportProvider>
    ): AddModuleWizard? {
      return selectFileAndCreateWizard(project = project,
                                       dialogParent = dialogParent,
                                       descriptor = descriptor,
                                       validateSelectedFile = Predicate { true },
                                       providers = providers)
    }

    @JvmStatic
    fun selectFileAndCreateWizard(
      project: Project?,
      dialogParent: Component?,
      descriptor: FileChooserDescriptor,
      validateSelectedFile: Predicate<VirtualFile>,
      vararg providers: ProjectImportProvider
    ): AddModuleWizard? {
      val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, dialogParent)
      var toSelect: VirtualFile? = null
      val lastLocation = PropertiesComponent.getInstance().getValue(LAST_IMPORTED_LOCATION)
      if (lastLocation != null) {
        toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation)
      }

      val files = chooser.choose(project, toSelect)
      val file = files.firstOrNull() ?: return null
      if (project == null) {
        // wizard will create a new project
        findAndFocusExistingProjectForPath(file.toNioPath())
      }
      if (!validateSelectedFile.test(file)) {
        return null
      }

      PropertiesComponent.getInstance().setValue(LAST_IMPORTED_LOCATION, file.path)
      return createImportWizard(project, dialogParent, file, *providers)
    }

    @JvmStatic
    fun getProviders(project: Project?): List<ProjectImportProvider> {
      val providers = ProjectImportProvider.PROJECT_IMPORT_PROVIDER.extensions
      return providers.filter { if (project == null) it.canCreateNewProject() else it.canImportModule() }
    }

    @JvmStatic
    fun createImportWizard(project: Project?,
                           dialogParent: Component?,
                           file: VirtualFile,
                           vararg providers: ProjectImportProvider): AddModuleWizard? {
      val available = providers.filter { it.canImport(file, project) }
      if (available.isEmpty()) {
        Messages.showInfoMessage(project, JavaUiBundle.message("message.cannot.import.anything.from.0", file.path),
                                 JavaUiBundle.message("dialog.title.cannot.import"))
        return null
      }

      val path = if (available.size == 1) {
        available[0].getPathToBeImported(file)
      }
      else {
        ProjectImportProvider.getDefaultPath(file)
      }
      val availableProviders = available.toTypedArray()
      if (dialogParent == null) {
        return AddModuleWizard(project, path, *availableProviders)
      }
      else {
        return AddModuleWizard(project, dialogParent, path, *availableProviders)
      }
    }

    private fun doCreateFromWizard(project: Project?, wizard: AbstractProjectWizard): List<Module> {
      if (project == null) {
        val newProject = importProject(wizard)
        return newProject?.modules?.asList() ?: emptyList()
      }
      else {
        return importModule(project, wizard)
      }
    }

    private fun importProject(wizard: AbstractProjectWizard): Project? {
      return when (val builder = wizard.projectBuilder) {
        is DeprecatedProjectBuilderForImport -> {
          openProject(builder, wizard)
        }
        else -> {
          NewProjectUtil.createFromWizard(wizard)
        }
      }
    }

    private fun openProject(builder: DeprecatedProjectBuilderForImport, wizard: AbstractProjectWizard): Project? {
      // the path to remove import action
      val projectPath = Path.of(wizard.newProjectFilePath)
      val file = ProjectUtilCore.getFileAndRefresh(projectPath)
      if (file == null) {
        LOG.warn(String.format("Cannot find project file in vfs `%s`", projectPath))
        return null
      }

      val openProcessor = builder.getProjectOpenProcessor()
      return runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
        // openProjectAsync must be implemented
        openProcessor.openProjectAsync(virtualFile = file, projectToClose = null, forceOpenInNewFrame = false)
      }
    }

    private fun importModule(project: Project, wizard: AbstractProjectWizard): List<Module> {
      val builder = wizard.projectBuilder
      try {
        return when {
          wizard.stepCount > 0 ->
            listOfNotNull(NewModuleAction().createModuleFromWizard(project, null, wizard))
          builder == null ->
            emptyList()
          builder.validate(project, project) ->
            builder.commit(project) ?: emptyList()
          else ->
            emptyList()
        }
      }
      finally {
        builder?.cleanup()
      }
    }

    private fun getFileChooserDescription(providers: List<ProjectImportProvider>): @NlsContexts.Label String {
      val builder = HtmlBuilder().append(JavaUiBundle.message("select")).append(" ")
      var first = true
      if (providers.isNotEmpty()) {
        for (provider in providers) {
          val sample = provider.fileSample ?: continue
          if (!first) {
            builder.append(", ").br()
          }
          else {
            first = false
          }
          builder.appendRaw(sample)
        }
      }
      builder.append(".")
      return builder.wrapWith("html").toString()
    }
  }
}
