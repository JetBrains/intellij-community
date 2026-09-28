// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.ide.actions.ImportModuleAction
import com.intellij.ide.impl.NewProjectUtil
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard
import com.intellij.ide.util.newProjectWizard.SelectTemplateSettings
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.Step
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.roots.ui.configuration.actions.NewModuleAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.projectImport.ProjectImportProvider
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.common.runAllSuspend
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/**
 * Drives an [AbstractProjectWizard] in a JUnit 5 test.
 * The fixture creates projects and modules from templates, imports projects and modules,
 * and closes every project that it created after the test.
 * It is the JUnit 5 counterpart of [ProjectWizardTestCase].
 */
@TestOnly
interface ProjectWizardTestFixture {
  /** The project that receives new and imported modules. */
  val project: Project

  /** The directory that receives the projects that the wizard creates. */
  val contentRoot: Path

  /** The wizard of the last operation, or null before the first one. */
  val wizard: AbstractProjectWizard?

  /** A disposable that the fixture disposes after the test. */
  val testDisposable: Disposable

  /** Runs the new project wizard with [adjuster] applied to every step and opens the created project. */
  suspend fun createProject(adjuster: ((Step) -> Unit)?): Project

  /** Creates a project from the template [group] and lets [adjuster] configure the template step. */
  suspend fun createProjectFromTemplate(group: String, adjuster: (NewProjectWizardStep) -> Unit): Project

  /** Runs the new module wizard for [project] with [adjuster] applied to every step. */
  suspend fun createModule(project: Project, adjuster: ((Step) -> Unit)?): Module

  /** Creates a module in [project] from the template [group] and lets [adjuster] configure the template step. */
  suspend fun createModuleFromTemplate(project: Project, group: String, adjuster: (NewProjectWizardStep) -> Unit): Module

  /** Imports a new project from [path] with the first applicable provider and returns its first module. */
  suspend fun importProjectFrom(path: String, adjuster: ((Step) -> Unit)?, vararg providers: ProjectImportProvider): Module

  /** Imports a module from [path] into [project]. */
  suspend fun importModuleFrom(provider: ProjectImportProvider, path: String): Module

  /** Waits until the indexes and the configuration activities of [project] are complete. */
  suspend fun waitForConfiguration(project: Project)

  /** Registers [sdk] in the JDK table until the end of the test. */
  fun addSdk(sdk: Sdk)

  /** Creates an SDK of [sdkType] and registers it in the JDK table until the end of the test. */
  fun createSdk(name: String, sdkType: SdkTypeId): Sdk
}

/**
 * Creates a [ProjectWizardTestFixture] over the project of [projectFixture].
 * [wizardFactory] creates the wizard for a project, or for a new project directory when the project is null.
 * The default factory creates a [NewProjectWizard].
 */
@TestOnly
fun projectWizardFixture(
  projectFixture: TestFixture<Project>,
  contentRootFixture: TestFixture<Path> = tempPathFixture(),
  wizardFactory: (project: Project?, directory: Path?) -> AbstractProjectWizard = ::newProjectWizard,
): TestFixture<ProjectWizardTestFixture> = testFixture("project wizard") {
  val project = projectFixture.init()
  val contentRoot = contentRootFixture.init()
  val fixture = ProjectWizardTestFixtureImpl(project, contentRoot, wizardFactory)
  fixture.setUp()
  initialized(fixture) {
    fixture.tearDown()
  }
}

private fun newProjectWizard(project: Project?, directory: Path?): AbstractProjectWizard {
  val modulesProvider = if (project == null) ModulesProvider.EMPTY_MODULES_PROVIDER else DefaultModulesProvider(project)
  return NewProjectWizard(project, modulesProvider, directory?.toString())
}

private class ProjectWizardTestFixtureImpl(
  override val project: Project,
  override val contentRoot: Path,
  private val wizardFactory: (project: Project?, directory: Path?) -> AbstractProjectWizard,
) : ProjectWizardTestFixture {
  override val testDisposable: Disposable = Disposer.newDisposable("ProjectWizardTestFixture")

  override var wizard: AbstractProjectWizard? = null
    private set

  private val createdProjects = ArrayList<Project>()
  private val defaultProject: Project get() = ProjectManager.getInstance().defaultProject
  private var oldDefaultProjectSdk: Sdk? = null

  fun setUp() {
    oldDefaultProjectSdk = ProjectRootManager.getInstance(defaultProject).projectSdk
    ProjectTypeStep.resetGroupForTests()
  }

  suspend fun tearDown() {
    runAllSuspend(
      { setWizard(null) },
      { closeCreatedProjects() },
      { resetDefaultProject() },
      { Disposer.dispose(testDisposable) },
    )
  }

  private suspend fun closeCreatedProjects() {
    val projectManager = ProjectManagerEx.getInstanceEx()
    for (createdProject in createdProjects.reversed()) {
      if (!createdProject.isDisposed) {
        projectManager.forceCloseProjectAsync(createdProject, save = false)
      }
    }
    createdProjects.clear()
  }

  private suspend fun resetDefaultProject() {
    withContext(Dispatchers.EDT) {
      WriteAction.run<RuntimeException> {
        LanguageLevelProjectExtensionImpl.getInstanceImpl(defaultProject).resetDefaults()
        ProjectRootManager.getInstance(defaultProject).projectSdk = oldDefaultProjectSdk
        JavaAwareProjectJdkTableImpl.removeInternalJdkInTests()
      }
      SelectTemplateSettings.getInstance().setLastTemplate(null, null)
      // let the VFS update pass
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
  }

  private fun setWizard(wizard: AbstractProjectWizard?) {
    this.wizard?.let { Disposer.dispose(it.disposable) }
    this.wizard = wizard
  }

  private fun createWizard(project: Project?) {
    if (project != null) {
      StandardFileSystems.local().refreshAndFindFileByPath(project.basePath!!)
    }
    val directory = if (project == null) Files.createTempDirectory(contentRoot, "project") else null
    setWizard(wizardFactory(project, directory))
    // to make default selection applied
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun runWizard(adjuster: ((Step) -> Unit)?) {
    val wizard = checkNotNull(wizard)
    while (true) {
      val currentStep = wizard.currentStepObject
      adjuster?.invoke(currentStep)
      if (wizard.isLast) {
        break
      }
      wizard.doNextAction()
      if (currentStep === wizard.currentStepObject) {
        throw IllegalStateException("$currentStep is not validated")
      }
    }
    if (!wizard.doFinishAction()) {
      throw IllegalStateException("${wizard.currentStepObject} is not validated")
    }
  }

  override suspend fun createProject(adjuster: ((Step) -> Unit)?): Project {
    val project = withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        createWizard(null)
        runWizard(adjuster)
        wizard!!.disposeIfNeeded()
        createProjectFromWizard()
      }
    }
    waitForConfiguration(project)
    return project
  }

  private fun createProjectFromWizard(): Project {
    val wizard = checkNotNull(wizard)
    val project = try {
      NewProjectUtil.createFromWizard(wizard)
    }
    catch (e: Throwable) {
      ProjectManager.getInstance().openProjects.find { it.name == wizard.projectName }?.let { createdProjects.add(it) }
      throw e
    }
    checkNotNull(project) { "The wizard created no project" }
    createdProjects.add(project)
    return project
  }

  override suspend fun createProjectFromTemplate(group: String, adjuster: (NewProjectWizardStep) -> Unit): Project {
    return createProject { step ->
      setSelectedTemplate(step, group)
      adjustSelectedStep(step, adjuster)
    }
  }

  override suspend fun createModule(project: Project, adjuster: ((Step) -> Unit)?): Module {
    val module = withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        createWizard(project)
        runWizard(adjuster)
        wizard!!.disposeIfNeeded()
        NewModuleAction().createModuleFromWizard(project, null, wizard!!)
      }
    }
    checkNotNull(module) { "The wizard created no module" }
    waitForConfiguration(project)
    return module
  }

  override suspend fun createModuleFromTemplate(project: Project, group: String, adjuster: (NewProjectWizardStep) -> Unit): Module {
    return createModule(project) { step ->
      setSelectedTemplate(step, group)
      adjustSelectedStep(step, adjuster)
    }
  }

  override suspend fun importProjectFrom(path: String, adjuster: ((Step) -> Unit)?, vararg providers: ProjectImportProvider): Module {
    val module = computeInWriteSafeContext { doImportModule(path, null, adjuster, providers) }
    createdProjects.add(module.project)
    return module
  }

  override suspend fun importModuleFrom(provider: ProjectImportProvider, path: String): Module {
    return computeInWriteSafeContext { doImportModule(path, project, null, arrayOf(provider)) }
  }

  private fun doImportModule(path: String, project: Project?, adjuster: ((Step) -> Unit)?, providers: Array<out ProjectImportProvider>): Module {
    val file = checkNotNull(StandardFileSystems.local().refreshAndFindFileByPath(path)) { "Can't find $path" }
    check(providers[0].canImport(file, project)) { "${providers[0]} cannot import $path" }
    val wizard = checkNotNull(ImportModuleAction.createImportWizard(project, null, file, *providers)) { "No import wizard for $path" }
    setWizard(wizard)
    if (wizard.stepCount > 0) {
      runWizard(adjuster)
    }
    return checkNotNull(ImportModuleAction.createFromWizard(project, wizard).firstOrNull()) { "The import of $path created no module" }
  }

  /** Runs [supplier] in a write-safe context: an `invokeLater` event that the test pumps at once. */
  private suspend fun <T> computeInWriteSafeContext(supplier: () -> T): T {
    return withContext(Dispatchers.EDT) {
      val result = Ref<T>()
      ApplicationManager.getApplication().invokeLater { result.set(supplier()) }
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      result.get()
    }
  }

  override suspend fun waitForConfiguration(project: Project) {
    withContext(Dispatchers.EDT) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    TestObservation.awaitConfiguration(project, 10.minutes)
  }

  override fun addSdk(sdk: Sdk) {
    WriteAction.runAndWait<RuntimeException> {
      ProjectJdkTable.getInstance().addJdk(sdk, testDisposable)
    }
  }

  override fun createSdk(name: String, sdkType: SdkTypeId): Sdk {
    val sdk = ProjectJdkTable.getInstance().createSdk(name, sdkType)
    addSdk(sdk)
    return sdk
  }
}

private fun setSelectedTemplate(step: Step, group: String) {
  val projectTypeStep = step as? ProjectTypeStep ?: throw IllegalStateException("$step is not a ProjectTypeStep")
  if (!projectTypeStep.setSelectedTemplate(group, null)) {
    throw IllegalArgumentException("$group template not found. Available groups: ${projectTypeStep.availableTemplateGroupsToString()}")
  }
}

private fun adjustSelectedStep(step: Step, adjuster: (NewProjectWizardStep) -> Unit) {
  val projectTypeStep = step as? ProjectTypeStep ?: throw IllegalStateException("$step is not a ProjectTypeStep")
  val customStep = projectTypeStep.customStep as? NewProjectWizardStep
                   ?: throw IllegalStateException("${projectTypeStep.customStep} is not a NewProjectWizardStep")
  adjuster(customStep)
}
