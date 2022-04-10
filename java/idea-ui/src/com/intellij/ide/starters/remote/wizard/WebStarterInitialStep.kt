// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.remote.wizard

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.starters.local.StarterModuleBuilder
import com.intellij.ide.starters.remote.*
import com.intellij.ide.starters.shared.*
import com.intellij.ide.starters.shared.ValidationFunctions.*
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.wizard.AbstractWizard
import com.intellij.ide.wizard.withVisualPadding
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.util.trim
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ui.configuration.validateJavaVersion
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.InplaceButton
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.*
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.UIUtil
import java.awt.event.ActionListener
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.Future
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.SwingUtilities

open class WebStarterInitialStep(contextProvider: WebStarterContextProvider) : CommonStarterInitialStep(
  contextProvider.wizardContext,
  contextProvider.starterContext,
  contextProvider.moduleBuilder,
  contextProvider.parentDisposable,
  contextProvider.settings
) {
  protected val moduleBuilder: WebStarterModuleBuilder = contextProvider.moduleBuilder
  protected val starterContext: WebStarterContext = contextProvider.starterContext

  private val packageNameProperty: GraphProperty<String> = propertyGraph.lazyProperty { starterContext.packageName }

  private val packagingProperty: GraphProperty<StarterAppPackaging> = propertyGraph.lazyProperty {
    starterContext.packaging ?: StarterAppPackaging("unknown", "")
  }
  private val languageLevelProperty: GraphProperty<StarterLanguageLevel> = propertyGraph.lazyProperty {
    starterContext.languageLevel ?: StarterLanguageLevel("unknown", "", "")
  }

  private var languageLevel: StarterLanguageLevel by languageLevelProperty
  private var packageName: String by packageNameProperty.trim()

  private val contentPanel: DialogPanel by lazy { createComponent() }
  private val progressIcon: AsyncProcessIcon by lazy { AsyncProcessIcon(moduleBuilder.builderId + "ServerOptions") }
  private val serverUrlLink: ActionLink by lazy { createServerUrlLink() }
  private val retryButton: InplaceButton by lazy { createRetryButton() }

  private val languageLevelsModel: DefaultComboBoxModel<StarterLanguageLevel> = DefaultComboBoxModel<StarterLanguageLevel>()
  private val applicationTypesModel: DefaultComboBoxModel<StarterAppType> = DefaultComboBoxModel<StarterAppType>()

  private lateinit var projectTypesSelector: SegmentedButton<StarterProjectType>
  private lateinit var packagingTypesSelector: SegmentedButton<StarterAppPackaging>
  private lateinit var languagesSelector: SegmentedButton<StarterLanguage>

  private var languages: List<StarterLanguage> = starterSettings.languages
  private var applicationTypes: List<StarterAppType> = starterSettings.applicationTypes
  private var projectTypes: List<StarterProjectType> = starterSettings.projectTypes
  private var packagingTypes: List<StarterAppPackaging> = starterSettings.packagingTypes

  @Volatile
  private var serverOptions: WebStarterServerOptions? = null

  @Volatile
  private var currentRequest: Future<*>? = null

  @Volatile
  private var isDisposed: Boolean = false

  private val serverOptionsLoadingSemaphore: Semaphore = Semaphore()
  private val serverSettingsButton: InplaceButton = InplaceButton(
    IconButton(JavaStartersBundle.message("button.tooltip.configure"), AllIcons.General.Gear, AllIcons.General.GearHover),
    ActionListener {
      configureServer()
    }
  )

  init {
    Disposer.register(parentDisposable, Disposable {
      isDisposed = true
      currentRequest?.cancel(true)
    })
  }

  override fun getComponent(): JComponent = contentPanel

  override fun getHelpId(): String? = moduleBuilder.getHelpId()

  override fun updateDataModel() {
    starterContext.serverOptions = this.serverOptions!!

    starterContext.projectType = projectTypeProperty.get()
    starterContext.language = languageProperty.get()
    starterContext.group = groupId
    starterContext.artifact = artifactId
    starterContext.name = entityName
    starterContext.packageName = packageName
    starterContext.packaging = packagingProperty.get()
    starterContext.languageLevel = languageLevel
    starterContext.testFramework = testFrameworkProperty.get()
    starterContext.applicationType = applicationTypeProperty.get()
    starterContext.includeExamples = exampleCodeProperty.get()
    starterContext.gitIntegration = gitProperty.get()

    wizardContext.projectName = entityName
    wizardContext.setProjectFileDirectory(FileUtil.join(location, entityName))

    val sdk = sdkProperty.get()
    if (wizardContext.project == null) {
      wizardContext.projectJdk = sdk
    }
    else {
      moduleBuilder.moduleJdk = sdk
    }
  }

  private fun suggestPackageName(): String {
    return StarterModuleBuilder.suggestPackageName(groupId, artifactId)
  }

  private fun createComponent(): DialogPanel {
    entityNameProperty.dependsOn(artifactIdProperty) { artifactId }
    artifactIdProperty.dependsOn(entityNameProperty) { entityName }

    packageNameProperty.dependsOn(artifactIdProperty, ::suggestPackageName)
    packageNameProperty.dependsOn(groupIdProperty, ::suggestPackageName)

    progressIcon.toolTipText = JavaStartersBundle.message("message.state.connecting.and.retrieving.options")

    return panel {
      row {
        label(JavaStartersBundle.message("title.project.server.url.label"))

        cell(serverUrlLink)
        cell(serverSettingsButton)
        cell(retryButton)
        cell(progressIcon)
      }.bottomGap(BottomGap.SMALL)

      addProjectLocationUi()

      addFieldsBefore(this)

      if (starterSettings.languages.size > 1) {
        row(JavaStartersBundle.message("title.project.language.label")) {
          languagesSelector = segmentedButton(starterSettings.languages, StarterLanguage::title)
            .bind(languageProperty)

          bottomGap(BottomGap.SMALL)
        }
      }

      if (starterSettings.projectTypes.isNotEmpty()) {
        val messages = starterSettings.customizedMessages
        row(messages?.projectTypeLabel ?: JavaStartersBundle.message("title.project.type.label")) {
          projectTypesSelector = segmentedButton(starterSettings.projectTypes, StarterProjectType::title)
            .bind(projectTypeProperty)

          bottomGap(BottomGap.SMALL)
        }
      }

      if (starterSettings.testFrameworks.isNotEmpty()) {
        row(JavaStartersBundle.message("title.project.test.framework.label")) {
          segmentedButton(starterSettings.testFrameworks, StarterTestRunner::title)
            .bind(testFrameworkProperty)
        }.bottomGap(BottomGap.SMALL)
      }

      addGroupArtifactUi()

      if (starterSettings.isPackageNameEditable) {
        row(JavaStartersBundle.message("title.project.package.label")) {
          textField()
            .bindText(packageNameProperty)
            .columns(COLUMNS_MEDIUM)
            .withSpecialValidation(CHECK_NOT_EMPTY, CHECK_NO_WHITESPACES, CHECK_NO_RESERVED_WORDS, CHECK_PACKAGE_NAME)
        }.bottomGap(BottomGap.SMALL)
      }

      if (starterSettings.applicationTypes.isNotEmpty()) {
        row(JavaStartersBundle.message("title.project.app.type.label")) {
          applicationTypesModel.addAll(starterSettings.applicationTypes)
          comboBox(applicationTypesModel, SimpleListCellRenderer.create("") { it?.title ?: "" })
            .bindItem(applicationTypeProperty)
            .columns(COLUMNS_MEDIUM)
        }.bottomGap(BottomGap.SMALL)
      }

      addSdkUi()

      if (starterSettings.languageLevels.isNotEmpty()) {
        row(JavaStartersBundle.message("title.project.java.version.label")) {
          languageLevelsModel.addAll(starterSettings.languageLevels)
          comboBox(languageLevelsModel, SimpleListCellRenderer.create("") { it?.title ?: "" })
            .bindItem(languageLevelProperty)
        }.bottomGap(BottomGap.SMALL)
      }

      if (starterSettings.packagingTypes.isNotEmpty()) {
        row(JavaStartersBundle.message("title.project.packaging.label")) {
          packagingTypesSelector = segmentedButton(starterSettings.packagingTypes, StarterAppPackaging::title)
            .bind(packagingProperty)
        }.bottomGap(BottomGap.SMALL)
      }

      addSampleCodeUi()

      addFieldsAfter(this)
    }.withVisualPadding()
  }

  private fun createServerUrlLink(): ActionLink {
    val result = ActionLink(urlPreview(starterContext.serverUrl)) {
      BrowserUtil.browse(starterContext.serverUrl)
    }
    UIUtil.applyStyle(UIUtil.ComponentStyle.REGULAR, result)
    return result
  }

  private fun createRetryButton(): InplaceButton {
    return InplaceButton(IconButton(JavaStartersBundle.message("button.tooltip.retry"),
                                    AllIcons.Nodes.ErrorIntroduction, AllIcons.Actions.ForceRefresh), ActionListener {
      requestServerOptions()
    }).apply {
      isVisible = false
    }
  }

  @NlsSafe
  private fun urlPreview(serverUrl: String): String {
    val url = serverUrl
      .removePrefix("https://")
      .removeSuffix("/")

    if (url.length > 35) {
      return url.take(30) + "..."
    }
    return url
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return contentPanel.preferredFocusedComponent
  }

  override fun validate(): Boolean {
    if (!validateFormFields(component, contentPanel, validatedTextComponents)) {
      return false
    }
    val passTechnologyName = if (starterSettings.languageLevels.size > 1) null else moduleBuilder.presentableName
    if (languageLevel.javaVersion.isNotBlank() &&
        !validateJavaVersion(sdkProperty, languageLevel.javaVersion, passTechnologyName)) {
      return false
    }

    return checkServerOptionsLoaded()
  }

  private fun checkServerOptionsLoaded(): Boolean {
    val request = currentRequest
    if (serverOptions != null && request == null) {
      return true
    }

    if (request == null) {
      // failure? retry server options loading
      requestServerOptions()
    }

    val newOptionsRef: Ref<WebStarterServerOptions> = Ref.create()

    ProgressManager.getInstance().runProcessWithProgressSynchronously(Runnable {
      val progressIndicator = ProgressManager.getInstance().progressIndicator
      progressIndicator.isIndeterminate = true

      for (i in 0 until 30) {
        progressIndicator.checkCanceled()
        if (serverOptionsLoadingSemaphore.waitFor(500)) {
          serverOptions?.let {
            newOptionsRef.set(it)
          }
          return@Runnable
        }
      }
    }, JavaStartersBundle.message("message.state.connecting.and.retrieving.options"), true, wizardContext.project)

    if (!newOptionsRef.isNull) {
      updatePropertiesWithServerOptions(newOptionsRef.get())
    }

    return serverOptions != null
  }

  override fun _init() {
    super._init()

    if (serverOptions == null && currentRequest == null) {
      @Suppress("HardCodedStringLiteral")
      val serverUrlFromSettings = PropertiesComponent.getInstance().getValue(getServerUrlPropertyName())
      if (serverUrlFromSettings != null) {
        setServerUrl(serverUrlFromSettings)
      }

      // required on dialog opening to get correct modality state
      SwingUtilities.invokeLater(this::requestServerOptions)
    }
  }

  private fun setServerUrl(@NlsSafe url: String) {
    starterContext.serverUrl = url
    serverUrlLink.text = urlPreview(url)
    serverUrlLink.toolTipText = url
  }

  private fun requestServerOptions() {
    progressIcon.isVisible = true
    retryButton.isVisible = false
    progressIcon.resume()

    serverOptionsLoadingSemaphore.down()

    currentRequest = ApplicationManager.getApplication().executeOnPooledThread {
      val readyServerOptions = try {
        moduleBuilder.getServerOptions(starterContext.serverUrl)
      }
      catch (e: Exception) {
        if (e is IOException || e is IllegalStateException) {
          logger<WebStarterInitialStep>().info("Unable to get server options for " + moduleBuilder.builderId, e)
        }
        else {
          logger<WebStarterInitialStep>().error("Unable to get server options for " + moduleBuilder.builderId, e)
        }

        ApplicationManager.getApplication().invokeLater(
          {
            if (component.isShowing) {
              // only if the wizard is visible
              Messages.showErrorDialog(
                JavaStartersBundle.message("message.no.connection.with.error.content", starterContext.serverUrl, e.message),
                JavaStartersBundle.message("message.title.error"))
            }
          }, getModalityState())

        null
      }

      setServerOptions(readyServerOptions)
    }
  }

  private fun setServerOptions(serverOptions: WebStarterServerOptions?) {
    this.serverOptions = serverOptions
    this.currentRequest = null
    this.serverOptionsLoadingSemaphore.up()

    ApplicationManager.getApplication().invokeLater(Runnable {
      progressIcon.suspend()
      progressIcon.isVisible = false
      retryButton.isVisible = serverOptions == null

      if (serverOptions != null) {
        updatePropertiesWithServerOptions(serverOptions)
      }
    }, getModalityState(), getDisposed())
  }

  private fun getModalityState(): ModalityState {
    return ModalityState.stateForComponent(wizardContext.getUserData(AbstractWizard.KEY)!!.contentComponent)
  }

  private fun getDisposed(): Condition<Any> = Condition<Any> { isDisposed }

  private fun configureServer() {
    val currentServerUrl = starterContext.serverUrl
    val serverUrlTitle = starterSettings.customizedMessages?.serverUrlDialogTitle
                         ?: JavaStartersBundle.message("title.server.url.dialog")

    val newUrl = Messages.showInputDialog(component, null, serverUrlTitle, null, currentServerUrl, object : InputValidator {
      override fun canClose(inputString: String?): Boolean = checkInput(inputString)

      override fun checkInput(inputString: String?): Boolean {
        try {
          URL(inputString)
        }
        catch (e: MalformedURLException) {
          return false
        }
        return true
      }
    })

    // update
    if (newUrl != null && starterContext.serverUrl != newUrl) {
      setServerUrl(newUrl)

      PropertiesComponent.getInstance().setValue(getServerUrlPropertyName(), newUrl)

      requestServerOptions()
    }
  }

  private fun getServerUrlPropertyName(): String {
    return moduleBuilder.builderId + ".service.url.last"
  }

  protected open fun updatePropertiesWithServerOptions(serverOptions: WebStarterServerOptions) {
    starterContext.frameworkVersion = serverOptions.frameworkVersions.find { it.isDefault }
                                      ?: serverOptions.frameworkVersions.firstOrNull()

    val currentPackageName = packageName // remember package name before applying group and artifact

    serverOptions.extractOption(SERVER_NAME_KEY) {
      if (entityName == suggestName(DEFAULT_MODULE_NAME)) {
        val newName = suggestName(it)
        if (entityName != newName) {
          entityNameProperty.set(newName)
        }
      }
    }
    serverOptions.extractOption(SERVER_GROUP_KEY) {
      if (groupId == DEFAULT_MODULE_GROUP && groupId != it) {
        groupIdProperty.set(it)
      }
    }
    serverOptions.extractOption(SERVER_ARTIFACT_KEY) {
      if (artifactId == DEFAULT_MODULE_ARTIFACT && artifactId != it) {
        artifactIdProperty.set(it)
      }
    }
    serverOptions.extractOption(SERVER_PACKAGE_NAME_KEY) {
      if (currentPackageName == DEFAULT_PACKAGE_NAME && currentPackageName != it) {
        packageNameProperty.set(it)
      }
    }
    serverOptions.extractOption(SERVER_VERSION_KEY) {
      starterContext.version = it
    }
    serverOptions.extractOption(SERVER_LANGUAGE_LEVELS_KEY) { levels ->
      val selectedItem = languageLevelsModel.selectedItem
      languageLevelsModel.removeAllElements()
      languageLevelsModel.addAll(levels)

      if (levels.contains(selectedItem)) {
        languageLevelsModel.selectedItem = selectedItem
      }
      else {
        languageLevel = levels.firstOrNull() ?: StarterLanguageLevel("unknown", "", "")
        languageLevelsModel.selectedItem = languageLevel
      }
    }
    serverOptions.extractOption(SERVER_LANGUAGE_LEVEL_KEY) { level ->
      if (languageLevel == starterSettings.defaultLanguageLevel && languageLevel != level) {
        languageLevelProperty.set(level)
      }
    }
    serverOptions.extractOption(SERVER_PROJECT_TYPES) { types ->
      if (types.isNotEmpty() && types != this.projectTypes && ::projectTypesSelector.isInitialized) {
        val correspondingOption = types.find { it.id == projectTypeProperty.get().id }
        projectTypeProperty.set(correspondingOption ?: types.first())
        projectTypesSelector.items(types)
        this.projectTypes = types
      }
    }
    serverOptions.extractOption(SERVER_APPLICATION_TYPES) { types ->
      if (types.isNotEmpty() && types != applicationTypes) {
        applicationTypesModel.removeAllElements()
        applicationTypesModel.addAll(types)
        applicationTypesModel.selectedItem = types.firstOrNull()
        this.applicationTypes = types
      }
    }
    serverOptions.extractOption(SERVER_PACKAGING_TYPES) { types ->
      if (types.isNotEmpty() && types != this.packagingTypes && ::packagingTypesSelector.isInitialized) {
        val correspondingOption = types.find { it.id == packagingProperty.get().id }
        packagingProperty.set(correspondingOption ?: types.first())
        packagingTypesSelector.items(types)
        this.packagingTypes = types
      }
    }
    serverOptions.extractOption(SERVER_LANGUAGES) { languages ->
      if (languages.isNotEmpty() && languages != this.languages && ::languagesSelector.isInitialized) {
        val correspondingOption = languages.find { it.id == languageProperty.get().id }
        languageProperty.set(correspondingOption ?: languages.first())
        languagesSelector.items(languages)
        this.languages = languages
      }
    }

    contentPanel.revalidate()
  }
}
