// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.remote

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.projectWizard.ProjectSettingsStep
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.importModule
import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.openSampleFiles
import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.preprocessModuleCreated
import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.preprocessModuleOpened
import com.intellij.ide.starters.remote.wizard.WebStarterInitialStep
import com.intellij.ide.starters.remote.wizard.WebStarterLibrariesStep
import com.intellij.ide.starters.shared.*
import com.intellij.ide.util.projectWizard.*
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.roots.ui.configuration.setupNewModuleJdk
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.Url
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpRequests.RequestProcessor
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.swing.Icon

abstract class WebStarterModuleBuilder : ModuleBuilder() {
  protected val starterContext: WebStarterContext = WebStarterContext()
  private val starterSettings: StarterWizardSettings by lazy { createSettings() }

  override fun getModuleType(): ModuleType<*> = StdModuleTypes.JAVA
  override fun getParentGroup(): String = JavaModuleType.BUILD_TOOLS_GROUP
  override fun getWeight(): Int = JavaModuleBuilder.BUILD_SYSTEM_WEIGHT + 10
  open fun getHelpId(): String? = null

  // Required settings

  abstract override fun getBuilderId(): String
  abstract override fun getNodeIcon(): Icon?
  abstract override fun getPresentableName(): String
  abstract override fun getDescription(): String

  abstract fun getDefaultServerUrl(): String

  protected abstract fun getLanguages(): List<StarterLanguage>
  protected abstract fun getProjectTypes(): List<StarterProjectType>

  // Optional settings

  protected open fun getDefaultVersion(): String = DEFAULT_MODULE_VERSION
  protected open fun isPackageNameEditable(): Boolean = false
  protected open fun isExampleCodeProvided(): Boolean = false
  protected open fun getTestFrameworks(): List<StarterTestRunner> = emptyList()
  protected open fun getLanguageLevels(): List<StarterLanguageLevel> = emptyList()
  protected open fun getDefaultLanguageLevel(): StarterLanguageLevel? = null
  protected open fun getApplicationTypes(): List<StarterAppType> = emptyList()
  protected open fun getPackagingTypes(): List<StarterAppPackaging> = emptyList()

  protected open fun getFilePathsToOpen(): List<String> = emptyList()

  override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean {
    return sdkType is JavaSdkType && !sdkType.isDependent
  }

  private fun createSettings(): StarterWizardSettings {
    return StarterWizardSettings(
      getProjectTypes(),
      getLanguages(),
      isExampleCodeProvided(),
      isPackageNameEditable(),
      getLanguageLevels(),
      getDefaultLanguageLevel(),
      getPackagingTypes(),
      getApplicationTypes(),
      getTestFrameworks(),
      getCustomizedMessages()
    )
  }

  override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep? {
    starterContext.serverUrl = getDefaultServerUrl()
    starterContext.version = getDefaultVersion()
    starterContext.language = starterSettings.languages.first()
    starterContext.projectType = starterSettings.projectTypes.firstOrNull()
    starterContext.isCreatingNewProject = context.isCreatingNewProject

    starterContext.applicationType = starterSettings.applicationTypes.firstOrNull()
    starterContext.languageLevel = starterSettings.defaultLanguageLevel ?: starterSettings.languageLevels.firstOrNull()
    starterContext.packaging = starterSettings.packagingTypes.firstOrNull()
    starterContext.testFramework = starterSettings.testFrameworks.firstOrNull()

    return createOptionsStep(WebStarterContextProvider(this, context, starterContext, starterSettings, parentDisposable))
  }

  override fun createWizardSteps(context: WizardContext, modulesProvider: ModulesProvider): Array<ModuleWizardStep> {
    return arrayOf(createLibrariesStep(WebStarterContextProvider(this, context, starterContext, starterSettings, context.disposable)))
  }

  override fun getIgnoredSteps(): List<Class<out ModuleWizardStep>> {
    return listOf(ProjectSettingsStep::class.java)
  }

  override fun modifyProjectTypeStep(settingsStep: SettingsStep): ModuleWizardStep? {
    // do not add standard SDK selector at the top
    return null
  }

  protected open fun createOptionsStep(contextProvider: WebStarterContextProvider): WebStarterInitialStep {
    return WebStarterInitialStep(contextProvider)
  }

  protected open fun createLibrariesStep(contextProvider: WebStarterContextProvider): WebStarterLibrariesStep {
    return WebStarterLibrariesStep(contextProvider)
  }

  internal fun getUserAgentInternal(): String? = getUserAgent()

  protected open fun getUserAgent(): String? {
    return ApplicationNamesInfo.getInstance().fullProductName + "/" + ApplicationInfo.getInstance().fullVersion
  }

  protected open fun getCustomizedMessages(): CustomizedMessages? = null

  @RequiresBackgroundThread
  internal fun getServerOptions(serverUrl: String): WebStarterServerOptions = loadServerOptions(serverUrl)

  @RequiresBackgroundThread
  protected abstract fun loadServerOptions(serverUrl: String): WebStarterServerOptions

  internal fun getDependencyStateInternal(frameworkVersion: WebStarterFrameworkVersion, dependency: WebStarterDependency): DependencyState {
    return getDependencyState(frameworkVersion, dependency)
  }

  internal fun isVersionAvailableInternal(frameworkVersion: WebStarterFrameworkVersion): Boolean {
    return isVersionAvailable(frameworkVersion)
  }

  protected open fun getDependencyState(frameworkVersion: WebStarterFrameworkVersion, dependency: WebStarterDependency): DependencyState {
    return DependencyAvailable
  }

  protected open fun isVersionAvailable(frameworkVersion: WebStarterFrameworkVersion): Boolean {
    return true
  }

  internal fun getGeneratorUrlInternal(serverUrl: String, starterContext: WebStarterContext): Url {
    return composeGeneratorUrl(serverUrl, starterContext)
  }

  protected abstract fun composeGeneratorUrl(serverUrl: String, starterContext: WebStarterContext): Url

  @RequiresBackgroundThread
  protected abstract fun extractGeneratorResult(tempZipFile: File, contentEntryDir: File)

  protected open fun getPluginRecommendations(): List<PluginRecommendation> = emptyList()

  protected open fun isReformatAfterCreation(project: Project): Boolean = true

  @Throws(ConfigurationException::class)
  override fun setupModule(module: Module) {
    super.setupModule(module)

    val project = module.project
    if (starterContext.isCreatingNewProject) {
      // Needed to ignore postponed project refresh
      project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, java.lang.Boolean.TRUE)
      project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, java.lang.Boolean.TRUE)
    }

    preprocessModuleCreated(module, this, starterContext.frameworkVersion?.id)

    StartupManager.getInstance(project).runAfterOpened {
      // a hack to avoid "Assertion failed: Network shouldn't be accessed in EDT or inside read action"
      ApplicationManager.getApplication().invokeLater({ extractAndImport(module) },
                                                      ModalityState.NON_MODAL, module.disposed)
    }
  }

  override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
    val sdk = setupNewModuleJdk(modifiableRootModel, moduleJdk, starterContext.isCreatingNewProject)
    val moduleExt = modifiableRootModel.getModuleExtension(LanguageLevelModuleExtension::class.java)
    if (moduleExt != null && sdk != null) {
      val languageLevel = starterContext.languageLevel
      if (languageLevel != null) {
        val selectedVersion = JavaSdkVersion.fromVersionString(languageLevel.id)
        val sdkVersion = JavaSdk.getInstance().getVersion(sdk)
        if (selectedVersion != null && sdkVersion != null && sdkVersion.isAtLeast(selectedVersion)) {
          moduleExt.languageLevel = selectedVersion.maxLanguageLevel
        }
      }
    }
    doAddContentEntry(modifiableRootModel)
  }

  private fun extractAndImport(module: Module) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        try {
          extractTemplate()
        }
        catch (e: Exception) {
          logger<WebStarterModuleBuilder>().info(e)

          EdtExecutorService.getScheduledExecutorInstance().schedule(
            {
              var message = JavaStartersBundle.message("error.text.with.error.content", e.message)
              message = StringUtil.shortenTextWithEllipsis(message, 1024, 0) // exactly 1024 because why not
              Messages.showErrorDialog(message, presentableName)
            },
            3, TimeUnit.SECONDS)
        }
      }, JavaStartersBundle.message("message.state.preparing.template"), true, module.project)

    LocalFileSystem.getInstance().refresh(false) // to avoid IDEA-232806

    preprocessModuleOpened(module, this, starterContext.frameworkVersion?.id)

    if (isReformatAfterCreation(module.project)) {
      ReformatCodeProcessor(module.project, module, false).run()
    }

    openSampleFiles(module, getFilePathsToOpen())
    importModule(module)

    verifyIdePlugins(module.project)
  }

  private fun extractTemplate() {
    val downloadResult = starterContext.result!!
    val tempFile = downloadResult.tempFile

    val path: String = contentEntryPath!!
    val contentEntryDir = File(path)

    if (downloadResult.isZip) {
      extractGeneratorResult(tempFile, contentEntryDir)
      fixExecutableFlag(contentEntryDir, "gradlew")
      fixExecutableFlag(contentEntryDir, "mvnw")
    }
    else {
      FileUtil.copy(tempFile, File(contentEntryDir, downloadResult.filename))
    }

    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(contentEntryDir)
    VfsUtil.markDirtyAndRefresh(false, true, false, vf)
  }

  private fun verifyIdePlugins(project: Project) {
    val selectedDependenciesIds = starterContext.dependencies.map { it.id }.toSet()

    val requiredPluginIds: MutableSet<PluginId> = HashSet()
    for (pluginRecommendation in getPluginRecommendations()) {
      for (dependencyId in pluginRecommendation.dependencyIds) {
        if (selectedDependenciesIds.contains(dependencyId)) {
          requiredPluginIds.add(PluginId.getId(pluginRecommendation.pluginId))
          break
        }
      }
    }

    val toInstallOrEnable: MutableSet<PluginId> = HashSet()
    for (pluginId in requiredPluginIds) {
      val ideaPluginDescriptor = PluginManagerCore.getPlugin(pluginId)
      if (ideaPluginDescriptor == null || !ideaPluginDescriptor.isEnabled) {
        toInstallOrEnable.add(pluginId)
      }
    }

    if (toInstallOrEnable.isEmpty()) return

    notificationGroup
      .createNotification(IdeBundle.message("plugins.advertiser.plugins.suggestions.title"), IdeBundle.message("plugins.advertiser.plugins.suggestions.text"), NotificationType.INFORMATION)
      .addAction(NotificationAction.create(IdeBundle.message("plugins.advertiser.action.enable.plugins")) { _, notification ->
        installAndEnable(project, toInstallOrEnable) { notification.expire() }
      })
      .notify(project)
  }

  private fun fixExecutableFlag(containingDir: File, relativePath: String) {
    val toFix = File(containingDir, relativePath)
    if (toFix.exists()) {
      toFix.setExecutable(true, false)
    }
  }

  @RequiresBackgroundThread
  protected fun loadJsonData(url: String, accept: String? = null): JsonElement {
    return HttpRequests.request(url)
      .userAgent(getUserAgent())
      .accept(accept)
      .connectTimeout(10000)
      .connect(RequestProcessor { request ->
        val reader = try {
          request.reader
        }
        catch (e: IOException) {
          logger<WebStarterModuleBuilder>().info("IOException loading JSON response from " + request.url, e)
          throw IOException(HttpRequests.createErrorMessage(e, request, false), e)
        }

        val jsonRootElement = try {
          JsonParser.parseReader(JsonReader(reader).apply {
            isLenient = true
          })
        }
        catch (e: Throwable) {
          logger<WebStarterModuleBuilder>().info("Unable to read JSON response from " + request.url, e)
          throw IOException("Error parsing JSON response", e)
        }
        jsonRootElement ?: throw IOException("Error parsing JSON response: empty document")
      })
  }

  fun JsonObject.getNullable(field: String): JsonElement? {
    val element = this.get(field)
    if (element is JsonNull) {
      return null
    }
    return element
  }
}
