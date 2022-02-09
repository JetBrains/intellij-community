package com.intellij.ide.starters.local

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.ide.IdeBundle
import com.intellij.ide.projectWizard.ProjectSettingsStep
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.starters.StarterModuleImporter
import com.intellij.ide.starters.StarterModuleProcessListener
import com.intellij.ide.starters.local.generator.AssetsProcessor
import com.intellij.ide.starters.local.wizard.StarterInitialStep
import com.intellij.ide.starters.local.wizard.StarterLibrariesStep
import com.intellij.ide.starters.shared.*
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.roots.ui.configuration.setupNewModuleJdk
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ModalityUiUtil
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.Nullable
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.net.URL
import javax.swing.Icon

abstract class StarterModuleBuilder : ModuleBuilder() {

  companion object {
    @JvmStatic
    private val INVALID_PACKAGE_NAME_SYMBOL_PATTERN: Regex = Regex("[^a-zA-Z0-9_.]")

    @JvmStatic
    private val IMPORTER_EP_NAME: ExtensionPointName<StarterModuleImporter> =
      ExtensionPointName.create("com.intellij.starter.moduleImporter")

    @JvmStatic
    fun suggestPackageName(group: String, artifact: String): String {
      val groupPrefix = group.lowercase().split(".")
        .joinToString(".") { sanitizePackage(it) }

      return "$groupPrefix.${sanitizePackage(artifact)}"
    }

    @JvmStatic
    fun sanitizePackage(input: String): String {
      val fileName = FileUtil.sanitizeFileName(input, false)
      return fileName
        .replace("-", "")
        .replace(INVALID_PACKAGE_NAME_SYMBOL_PATTERN, "_")
        .lowercase()
    }

    @JvmStatic
    fun importModule(module: Module) {
      if (module.isDisposed) return
      val moduleBuilderPostTasks = IMPORTER_EP_NAME.extensions
      for (task in moduleBuilderPostTasks) {
        if (!task.runAfterSetup(module)) break
      }
    }

    @JvmStatic
    fun preprocessModuleCreated(module: Module, builder: ModuleBuilder, frameworkVersion: String?) {
      val project = module.project
      project.messageBus.syncPublisher(StarterModuleProcessListener.TOPIC)
        .moduleCreated(module, builder, frameworkVersion)
    }

    @JvmStatic
    fun preprocessModuleOpened(module: Module, builder: ModuleBuilder, frameworkVersion: String?) {
      val project = module.project
      project.messageBus.syncPublisher(StarterModuleProcessListener.TOPIC)
        .moduleOpened(module, builder, frameworkVersion)
    }

    @JvmStatic
    internal fun openSampleFiles(module: Module, filePathsToOpen: List<String>) {
      val contentRoot = module.rootManager.contentRoots.firstOrNull()
      if (contentRoot != null) {
        val fileEditorManager = FileEditorManager.getInstance(module.project)
        for (filePath in filePathsToOpen) {
          val fileToOpen = VfsUtil.findRelativeFile(filePath, contentRoot)
          if (fileToOpen != null) {
            fileEditorManager.openTextEditor(OpenFileDescriptor(module.project, fileToOpen), true)
          }
          else {
            logger<StarterModuleBuilder>().debug("Unable to find sample file $filePath in module: ${module.name}")
          }
        }
      }
    }

    @TestOnly
    fun StarterModuleBuilder.setupTestModule(module: Module, starterId: String? = null, consumer: StarterContext.() -> Unit) {
      this.apply {
        starterContext.starterPack = getStarterPack()
        moduleJdk = ModuleRootManager.getInstance(module).sdk

        starterContext.starter = if (starterId == null) {
          starterContext.starterPack.starters.first()
        }
        else {
          starterContext.starterPack.starters.find { it.id == starterId }
        }
        starterContext.starterDependencyConfig = loadTestDependencyConfig(starterContext.starter!!)
      }

      consumer.invoke(starterContext)

      ApplicationManager.getApplication().invokeAndWait {
        runWriteAction {
          setupModule(module)

          PsiDocumentManager.getInstance(module.project).commitAllDocuments()
          FileDocumentManager.getInstance().saveAllDocuments()
        }
      }
    }

    private fun loadTestDependencyConfig(starter: Starter): DependencyConfig {
      val starterDependencyDom = starter.versionConfigUrl.openStream().use { JDOMUtil.load(it) }
      return StarterUtils.parseDependencyConfig(starterDependencyDom, starter.versionConfigUrl.path, true)
    }
  }

  protected val starterContext: StarterContext = StarterContext()
  private val starterSettings: StarterWizardSettings by lazy { createSettings() }

  override fun getModuleType(): ModuleType<*> = StdModuleTypes.JAVA
  override fun getWeight(): Int = JVM_WEIGHT
  open fun getHelpId(): String? = null

  abstract override fun getBuilderId(): String
  abstract override fun getNodeIcon(): Icon?
  abstract override fun getPresentableName(): String
  abstract override fun getDescription(): String

  protected abstract fun getProjectTypes(): List<StarterProjectType>
  protected abstract fun getLanguages(): List<StarterLanguage>
  protected abstract fun getStarterPack(): StarterPack
  protected abstract fun getTestFrameworks(): List<StarterTestRunner>
  protected abstract fun getAssets(starter: Starter): List<GeneratorAsset>
  protected open fun isExampleCodeProvided(): Boolean = false
  protected open fun getMinJavaVersion(): JavaVersion? = LanguageLevel.JDK_1_8.toJavaVersion()

  protected open fun getCustomizedMessages(): CustomizedMessages? = null

  protected open fun getCollapsedDependencyCategories(): List<String> = emptyList()
  protected open fun getFilePathsToOpen(): List<String> = emptyList()

  internal open fun getCollapsedDependencyCategoriesInternal(): List<String> = getCollapsedDependencyCategories()

  internal fun isDependencyAvailableInternal(starter: Starter, dependency: Library): Boolean {
    return isDependencyAvailable(starter, dependency)
  }

  protected open fun isDependencyAvailable(starter: Starter, dependency: Library): Boolean {
    return true
  }

  override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean {
    return sdkType is JavaSdkType && !sdkType.isDependent
  }

  override fun modifyProjectTypeStep(settingsStep: SettingsStep): ModuleWizardStep? {
    // do not add standard SDK selector at the top
    return null
  }

  @Throws(ConfigurationException::class)
  override fun setupModule(module: Module) {
    super.setupModule(module)

    if (starterContext.isCreatingNewProject) {
      val project = module.project

      project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, java.lang.Boolean.TRUE)
      project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, java.lang.Boolean.TRUE)
    }

    startGenerator(module)
  }

  private fun createSettings(): StarterWizardSettings {
    return StarterWizardSettings(
      getProjectTypes(),
      getLanguages(),
      isExampleCodeProvided(),
      false,
      emptyList(),
      null,
      emptyList(),
      emptyList(),
      getTestFrameworks(),
      getCustomizedMessages()
    )
  }

  override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep {
    starterContext.language = starterSettings.languages.first()
    starterContext.testFramework = starterSettings.testFrameworks.firstOrNull()
    starterContext.projectType = starterSettings.projectTypes.firstOrNull()
    starterContext.applicationType = starterSettings.applicationTypes.firstOrNull()
    starterContext.isCreatingNewProject = context.isCreatingNewProject

    return createOptionsStep(StarterContextProvider(this, parentDisposable, starterContext, context, starterSettings, ::getStarterPack))
  }

  override fun createWizardSteps(context: WizardContext, modulesProvider: ModulesProvider): Array<ModuleWizardStep> {
    return arrayOf(createLibrariesStep(
      StarterContextProvider(this, context.disposable, starterContext, context, starterSettings, ::getStarterPack)
    ))
  }

  protected open fun createOptionsStep(contextProvider: StarterContextProvider): StarterInitialStep {
    return StarterInitialStep(contextProvider)
  }

  protected open fun createLibrariesStep(contextProvider: StarterContextProvider): StarterLibrariesStep {
    return StarterLibrariesStep(contextProvider)
  }

  override fun getIgnoredSteps(): List<Class<out ModuleWizardStep>> {
    return listOf(ProjectSettingsStep::class.java)
  }

  internal fun getMinJavaVersionInternal(): JavaVersion? = getMinJavaVersion()

  override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
    setupNewModuleJdk(modifiableRootModel, moduleJdk, starterContext.isCreatingNewProject)
    doAddContentEntry(modifiableRootModel)
  }

  internal fun loadDependencyConfigInternal(): Map<String, DependencyConfig> {
    return loadDependencyConfig()
  }

  protected fun loadDependencyConfig(): Map<String, DependencyConfig> {
    return starterContext.starterPack.starters.associate { starter ->
      starter.id to starter.versionConfigUrl.openStream().use {
        val dependencyConfigUpdates = starterContext.startersDependencyUpdates[starter.id]
        val dependencyConfigUpdatesVersion = dependencyConfigUpdates?.version?.let { version -> Version.parseVersion(version) }
                                             ?: Version(-1, -1, -1)

        val starterDependencyConfig = JDOMUtil.load(it)
        val starterDependencyConfigVersion = StarterUtils.parseDependencyConfigVersion(starterDependencyConfig,
                                                                                       starter.versionConfigUrl.path)

        val mergeDependencyUpdate = starterDependencyConfigVersion < dependencyConfigUpdatesVersion
        if (mergeDependencyUpdate) {
          StarterUtils.mergeDependencyConfigs(
            StarterUtils.parseDependencyConfig(starterDependencyConfig, starter.versionConfigUrl.path, false),
            dependencyConfigUpdates)
        }
        else {
          StarterUtils.parseDependencyConfig(starterDependencyConfig, starter.versionConfigUrl.path)
        }
      }
    }
  }

  @Throws(ConfigurationException::class)
  private fun startGenerator(module: Module) {
    val moduleContentRoot =
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(contentEntryPath!!.replace("\\", "/"))
        ?: throw IllegalStateException("Module root not found")
      }
      else {
        val contentEntries = ModuleRootManager.getInstance(module).contentEntries
        contentEntries.first { it.sourceFolders.isNotEmpty() }.file!!
      }

    val starter = starterContext.starter ?: throw IllegalStateException("Starter is not set")
    val dependencyConfig = starterContext.starterDependencyConfig ?: error("Starter dependency config is not set")
    val sdk = moduleJdk

    val rootPackage = suggestPackageName(starterContext.group, starterContext.artifact)

    val generatorContext = GeneratorContext(
      starter.id,
      module.name,
      starterContext.group,
      starterContext.artifact,
      starterContext.version,
      starterContext.testFramework?.id,
      rootPackage,
      sdk?.let { JavaSdk.getInstance().getVersion(it) },
      starterContext.language.id,
      starterContext.libraryIds,
      dependencyConfig,
      getGeneratorContextProperties(sdk, dependencyConfig),
      getAssets(starter),
      moduleContentRoot
    )

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      WriteAction.runAndWait<Throwable> {
        try {
          AssetsProcessor().generateSources(generatorContext, getTemplateProperties())
        }
        catch (e: IOException) {
          logger<StarterModuleBuilder>().error("Unable to create module by template", e)

          ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(
              JavaStartersBundle.message("starter.generation.error", e.message ?: ""),
              presentableName)
          }
          return@runAndWait
        }

        applyAdditionalChanges(module)
      }

      preprocessModuleCreated(module, this, starterContext.starter?.id)

      StartupManager.getInstance(module.project).runAfterOpened {  // IDEA-244863
        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.NON_MODAL, module.disposed, Runnable {
          if (module.isDisposed) return@Runnable

          ReformatCodeProcessor(module.project, module, false).run()
          // import of module may dispose it and create another, open files first
          openSampleFiles(module, getFilePathsToOpen())

          if (starterContext.gitIntegration) {
            runBackgroundableTask(IdeBundle.message("progress.title.creating.git.repository"), module.project) {
              GitRepositoryInitializer.getInstance()?.initRepository(module.project, moduleContentRoot)
            }
          }

          importModule(module)
        })
      }
    }
    else {
      // test mode, open files immediately, do not import module
      AssetsProcessor().generateSources(generatorContext, getTemplateProperties())
      ReformatCodeProcessor(module.project, module, false).run()
      openSampleFiles(module, getFilePathsToOpen())
    }
  }

  override fun doAddContentEntry(modifiableRootModel: ModifiableRootModel): ContentEntry? {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      // do not create new content entry
      return modifiableRootModel.contentEntries.first { it.sourceFolders.isNotEmpty() }
    }
    return super.doAddContentEntry(modifiableRootModel)
  }

  open fun getTemplateProperties(): Map<String, Any> = emptyMap()

  open fun applyAdditionalChanges(module: Module) {
    // optional hook method
  }

  protected fun getDependencyConfig(resourcePath: String): URL {
    return javaClass.getResource(resourcePath) ?: error("Failed to get resource: $resourcePath")
  }

  protected open fun getGeneratorContextProperties(sdk: @Nullable Sdk?, dependencyConfig: DependencyConfig): Map<String, String> {
    return emptyMap()
  }

  protected fun getSamplesExt(language: StarterLanguage): String {
    return when (language.id) {
      "java" -> "java"
      "groovy" -> "groovy"
      "kotlin" -> "kt"
      else -> throw UnsupportedOperationException()
    }
  }

  protected fun getPackagePath(group: String, artifact: String): String {
    val packageName = suggestPackageName(group, artifact)
    return packageName.replace(".", "/").removeSuffix("/")
  }
}
