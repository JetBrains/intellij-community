// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local.wizard

import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.shared.*
import com.intellij.ide.starters.shared.ValidationFunctions.*
import com.intellij.ide.util.projectWizard.ModuleNameGenerator
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.properties.map
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.openapi.roots.ui.configuration.validateJavaVersion
import com.intellij.openapi.roots.ui.configuration.validateSdk
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.EMPTY_LABEL
import com.intellij.ui.layout.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.exists
import com.intellij.util.text.nullize
import com.intellij.util.ui.UIUtil.invokeLaterIfNeeded
import org.jdom.Element
import java.io.File
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JTextField

open class StarterInitialStep(contextProvider: StarterContextProvider) : ModuleWizardStep() {

  protected val moduleBuilder: StarterModuleBuilder = contextProvider.moduleBuilder
  protected val wizardContext: WizardContext = contextProvider.wizardContext
  protected val starterContext: StarterContext = contextProvider.starterContext
  protected val starterSettings: StarterWizardSettings = contextProvider.settings
  protected val parentDisposable: Disposable = contextProvider.parentDisposable
  private val starterPackProvider: () -> StarterPack = contextProvider.starterPackProvider

  private val validatedTextComponents: MutableList<JTextField> = mutableListOf()

  protected val propertyGraph: PropertyGraph = PropertyGraph()
  private val entityNameProperty: GraphProperty<String> = propertyGraph.graphProperty(::suggestName)
  private val locationProperty: GraphProperty<String> = propertyGraph.graphProperty(::suggestLocationByName)
  private val groupIdProperty: GraphProperty<String> = propertyGraph.graphProperty { starterContext.group }
  private val artifactIdProperty: GraphProperty<String> = propertyGraph.graphProperty { entityName }
  protected val sdkProperty: GraphProperty<Sdk?> = propertyGraph.graphProperty { null }

  private val projectTypeProperty: GraphProperty<StarterProjectType?> = propertyGraph.graphProperty { starterContext.projectType }
  private val languageProperty: GraphProperty<StarterLanguage> = propertyGraph.graphProperty { starterContext.language }
  private val testFrameworkProperty: GraphProperty<StarterTestRunner?> = propertyGraph.graphProperty { starterContext.testFramework }
  private val applicationTypeProperty: GraphProperty<StarterAppType?> = propertyGraph.graphProperty { starterContext.applicationType }
  private val exampleCodeProperty: GraphProperty<Boolean> = propertyGraph.graphProperty { starterContext.includeExamples }

  private val gitProperty: GraphProperty<Boolean> = propertyGraph.graphProperty { false }

  private var entityName: String by entityNameProperty.map { it.trim() }
  private var location: String by locationProperty
  private var groupId: String by groupIdProperty.map { it.trim() }
  private var artifactId: String by artifactIdProperty.map { it.trim() }

  private val contentPanel: DialogPanel by lazy { createComponent() }

  private val sdkModel: ProjectSdksModel = ProjectSdksModel()
  protected lateinit var sdkComboBox: JdkComboBox

  protected lateinit var languageRow: Row
  protected lateinit var groupRow: Row
  protected lateinit var artifactRow: Row

  @Volatile
  private var isDisposed: Boolean = false

  override fun getHelpId(): String? = moduleBuilder.getHelpId()

  init {
    Disposer.register(parentDisposable, Disposable {
      isDisposed = true
      sdkModel.disposeUIResources()
    })
  }

  override fun updateDataModel() {
    starterContext.projectType = projectTypeProperty.get()
    starterContext.language = languageProperty.get()
    starterContext.group = groupId
    starterContext.artifact = artifactId
    starterContext.testFramework = testFrameworkProperty.get()
    starterContext.includeExamples = exampleCodeProperty.get()
    starterContext.gitIntegration = gitProperty.get()

    wizardContext.projectName = entityName
    wizardContext.setProjectFileDirectory(FileUtil.join(location, entityName))

    val sdk = sdkProperty.get()
    moduleBuilder.moduleJdk = sdk

    if (wizardContext.project == null) {
      wizardContext.projectJdk = sdk
    }
  }

  override fun getComponent(): JComponent {
    return contentPanel
  }

  private fun createComponent(): DialogPanel {
    entityNameProperty.dependsOn(artifactIdProperty) { artifactId }
    artifactIdProperty.dependsOn(entityNameProperty) { entityName }

    // query dependencies from builder, called only once
    val starterPack = starterPackProvider.invoke()
    starterContext.starterPack = starterPack

    updateStartersDependencies(starterPack)

    return panel {
      row(JavaStartersBundle.message("title.project.name.label")) {
        textField(entityNameProperty)
          .growPolicy(GrowPolicy.SHORT_TEXT)
          .withSpecialValidation(listOf(CHECK_NOT_EMPTY, CHECK_SIMPLE_NAME_FORMAT),
                                 createLocationWarningValidator(locationProperty))
          .focused()

        for (nameGenerator in ModuleNameGenerator.EP_NAME.extensionList) {
          val nameGeneratorUi = nameGenerator.getUi(moduleBuilder.builderId) { entityNameProperty.set(it) }
          if (nameGeneratorUi != null) {
            component(nameGeneratorUi).constraints(pushX)
          }
        }
      }.largeGapAfter()

      addProjectLocationUi()

      addFieldsBefore(this)

      if (starterSettings.applicationTypes.isNotEmpty()) {
        row(JavaStartersBundle.message("title.project.app.type.label")) {
          val applicationTypesModel = DefaultComboBoxModel<StarterAppType>()
          applicationTypesModel.addAll(starterSettings.applicationTypes)
          comboBox(applicationTypesModel, applicationTypeProperty, SimpleListCellRenderer.create("") { it?.title ?: "" })
            .growPolicy(GrowPolicy.SHORT_TEXT)
        }.largeGapAfter()
      }

      if (starterSettings.languages.size > 1) {
        row(JavaStartersBundle.message("title.project.language.label")) {
          languageRow = this

          segmentedButton(starterSettings.languages, languageProperty) { it.title }
        }.largeGapAfter()
      }

      if (starterSettings.projectTypes.isNotEmpty()) {
        val messages = starterSettings.customizedMessages
        row(messages?.projectTypeLabel ?: JavaStartersBundle.message("title.project.build.system.label")) {
          segmentedButton(starterSettings.projectTypes, projectTypeProperty) { it?.title ?: "" }
        }.largeGapAfter()
      }

      if (starterSettings.testFrameworks.isNotEmpty()) {
        row(JavaStartersBundle.message("title.project.test.framework.label")) {
          segmentedButton(starterSettings.testFrameworks, testFrameworkProperty) { it?.title ?: "" }
        }.largeGapAfter()
      }

      row(JavaStartersBundle.message("title.project.group.label")) {
        groupRow = this

        textField(groupIdProperty)
          .growPolicy(GrowPolicy.SHORT_TEXT)
          .withSpecialValidation(CHECK_NOT_EMPTY, CHECK_NO_WHITESPACES, CHECK_GROUP_FORMAT, CHECK_NO_RESERVED_WORDS)
      }.largeGapAfter()

      row(JavaStartersBundle.message("title.project.artifact.label")) {
        artifactRow = this

        textField(artifactIdProperty)
          .growPolicy(GrowPolicy.SHORT_TEXT)
          .withSpecialValidation(CHECK_NOT_EMPTY, CHECK_NO_WHITESPACES, CHECK_ARTIFACT_SIMPLE_FORMAT, CHECK_NO_RESERVED_WORDS)
      }.largeGapAfter()

      row(JavaStartersBundle.message("title.project.sdk.label")) {
        sdkComboBox = sdkComboBox(sdkModel, sdkProperty, wizardContext.project, moduleBuilder)
          .growPolicy(GrowPolicy.SHORT_TEXT)
          .component
      }.largeGapAfter()

      if (starterSettings.isExampleCodeProvided) {
        row {
          checkBox(JavaStartersBundle.message("title.project.examples.label"), exampleCodeProperty)
        }
      }

      addFieldsAfter(this)
    }.withVisualPadding(topField = true)
  }

  private fun LayoutBuilder.addProjectLocationUi() {
    val locationRow = row(JavaStartersBundle.message("title.project.location.label")) {
      projectLocationField(locationProperty, wizardContext)
        .withSpecialValidation(CHECK_NOT_EMPTY, CHECK_LOCATION_FOR_ERROR)
    }

    if (wizardContext.isCreatingNewProject) {
      // Git should not be enabled for single module
      row(EMPTY_LABEL) {
        checkBox(UIBundle.message("label.project.wizard.new.project.git.checkbox"), gitProperty)
      }.largeGapAfter()
    } else {
      locationRow.largeGapAfter()
    }
  }

  protected open fun addFieldsBefore(layout: LayoutBuilder) {}

  protected open fun addFieldsAfter(layout: LayoutBuilder) {}

  override fun validate(): Boolean {
    if (!validateFormFields(component, contentPanel, validatedTextComponents)) {
      return false
    }
    if (!validateSdk(sdkProperty, sdkModel)) {
      return false
    }
    if (!validateJavaVersion(sdkProperty, moduleBuilder.getMinJavaVersionInternal()?.toFeatureString(), moduleBuilder.presentableName)) {
      return false
    }
    return true
  }

  private fun updateStartersDependencies(starterPack: StarterPack) {
    val starters = starterPack.starters
    AppExecutorUtil.getAppExecutorService().submit {
      checkDependencyUpdates(starters)
    }
  }

  @RequiresBackgroundThread
  private fun checkDependencyUpdates(starters: List<Starter>) {
    for (starter in starters) {
      val localUpdates = loadStarterDependencyUpdatesFromFile(starter.id)
      if (localUpdates != null) {
        setStarterDependencyUpdates(starter.id, localUpdates)
        return
      }

      val externalUpdates = loadStarterDependencyUpdatesFromNetwork(starter.id) ?: return
      val (dependencyUpdates, resourcePath) = externalUpdates

      if (isDisposed) return

      val dependencyConfig = StarterUtils.parseDependencyConfig(dependencyUpdates, resourcePath)

      if (isDisposed) return

      saveStarterDependencyUpdatesToFile(starter.id, dependencyUpdates)

      setStarterDependencyUpdates(starter.id, dependencyConfig)
    }
  }

  private fun suggestName(): String {
    return suggestName(starterContext.artifact)
  }

  private fun suggestName(prefix: String): String {
    val projectFileDirectory = File(wizardContext.projectFileDirectory)
    return FileUtil.createSequentFileName(projectFileDirectory, prefix, "")
  }

  private fun suggestLocationByName(): String {
    return wizardContext.projectFileDirectory // no project name included
  }

  @RequiresBackgroundThread
  private fun loadStarterDependencyUpdatesFromFile(starterId: String): DependencyConfig? {
    val configUpdateDir = File(PathManager.getTempPath(), getDependencyConfigUpdatesDirLocation(starterId))
    val configUpdateFile = File(configUpdateDir, getPatchFileName(starterId))
    if (!configUpdateFile.exists()
        || StarterUtils.isDependencyUpdateFileExpired(configUpdateFile)) {
      return null
    }

    val resourcePath = configUpdateFile.absolutePath
    return try {
      StarterUtils.parseDependencyConfig(JDOMUtil.load(configUpdateFile), resourcePath)
    }
    catch (e: Exception) {
      logger<StarterInitialStep>().warn("Failed to load starter dependency updates from file: $resourcePath. The file will be deleted.")
      FileUtil.delete(configUpdateFile)
      null
    }
  }

  @RequiresBackgroundThread
  private fun loadStarterDependencyUpdatesFromNetwork(starterId: String): Pair<Element, String>? {
    val url = buildStarterPatchUrl(starterId) ?: return null
    return try {
      val content = HttpRequests.request(url)
        .accept("application/xml")
        .readString()
      return JDOMUtil.load(content) to url
    }
    catch (e: Exception) {
      if (e is HttpRequests.HttpStatusException
          && (e.statusCode == 403 || e.statusCode == 404)) {
        logger<StarterInitialStep>().debug("No updates for $starterId: $url")
      }
      else if (e is SocketTimeoutException) {
        logger<StarterInitialStep>().debug("Socket timeout for $starterId: $url")
      }
      else {
        logger<StarterInitialStep>().warn("Unable to load external starter $starterId dependency updates from: $url", e)
      }
      null
    }
  }

  @RequiresBackgroundThread
  private fun saveStarterDependencyUpdatesToFile(starterId: String, dependencyConfigUpdate: Element) {
    val configUpdateDir = Path.of(PathManager.getTempPath(), getDependencyConfigUpdatesDirLocation(starterId))
    if (!configUpdateDir.exists()) {
      Files.createDirectories(configUpdateDir)
    }

    val configUpdateFile = configUpdateDir.resolve(getPatchFileName(starterId))
    JDOMUtil.write(dependencyConfigUpdate, configUpdateFile)
  }

  private fun setStarterDependencyUpdates(starterId: String, dependencyConfigUpdate: DependencyConfig) {
    invokeLaterIfNeeded {
      if (isDisposed) return@invokeLaterIfNeeded

      starterContext.startersDependencyUpdates[starterId] = dependencyConfigUpdate
    }
  }

  private fun buildStarterPatchUrl(starterId: String): String? {
    val host = Registry.stringValue("starters.dependency.update.host").nullize(true) ?: return null
    val ideVersion = ApplicationInfoImpl.getShadowInstance().let { "${it.majorVersion}.${it.minorVersion}" }
    val patchFileName = getPatchFileName(starterId)

    return "$host/starter/$starterId/$ideVersion/$patchFileName"
  }

  private fun getDependencyConfigUpdatesDirLocation(starterId: String): String = "framework-starters/$starterId/"

  private fun getPatchFileName(starterId: String): String = "${starterId}_patch.pom"

  @Suppress("SameParameterValue")
  private fun <T : JComponent> CellBuilder<T>.withSpecialValidation(vararg errorValidationUnits: TextValidationFunction): CellBuilder<T> =
    withValidation(this, errorValidationUnits.asList(), null, validatedTextComponents, parentDisposable)

  private fun <T : JComponent> CellBuilder<T>.withSpecialValidation(
    errorValidationUnits: List<TextValidationFunction>,
    warningValidationUnit: TextValidationFunction?
  ): CellBuilder<T> {
    return withValidation(this, errorValidationUnits, warningValidationUnit, validatedTextComponents, parentDisposable)
  }
}