// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.local.wizard

import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.shared.*
import com.intellij.ide.wizard.withVisualPadding
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.ui.configuration.validateJavaVersion
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.*
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

open class StarterInitialStep(contextProvider: StarterContextProvider) : CommonStarterInitialStep(
  contextProvider.wizardContext,
  contextProvider.starterContext,
  contextProvider.moduleBuilder,
  contextProvider.parentDisposable,
  contextProvider.settings
) {
  protected val moduleBuilder: StarterModuleBuilder = contextProvider.moduleBuilder
  protected val starterContext: StarterContext = contextProvider.starterContext
  private val starterPackProvider: () -> StarterPack = contextProvider.starterPackProvider

  private val contentPanel: DialogPanel by lazy { createComponent() }

  protected lateinit var languageRow: Row

  @Volatile
  private var isDisposed: Boolean = false

  override fun getHelpId(): String? = moduleBuilder.getHelpId()

  init {
    Disposer.register(parentDisposable, Disposable {
      isDisposed = true
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
      addProjectLocationUi()

      addFieldsBefore(this)

      if (starterSettings.applicationTypes.isNotEmpty()) {
        row(JavaStartersBundle.message("title.project.app.type.label")) {
          val applicationTypesModel = DefaultComboBoxModel<StarterAppType>()
          applicationTypesModel.addAll(starterSettings.applicationTypes)
          comboBox(applicationTypesModel, SimpleListCellRenderer.create("", StarterAppType::title))
            .bindItem(applicationTypeProperty)
            .columns(COLUMNS_MEDIUM)

          bottomGap(BottomGap.SMALL)
        }
      }

      if (starterSettings.languages.size > 1) {
        row(JavaStartersBundle.message("title.project.language.label")) {
          languageRow = this

          segmentedButton(starterSettings.languages, StarterLanguage::title)
            .bind(languageProperty)

          bottomGap(BottomGap.SMALL)
        }
      }

      if (starterSettings.projectTypes.isNotEmpty()) {
        val messages = starterSettings.customizedMessages
        row(messages?.projectTypeLabel ?: JavaStartersBundle.message("title.project.build.system.label")) {
          segmentedButton(starterSettings.projectTypes, StarterProjectType::title)
            .bind(projectTypeProperty)

          bottomGap(BottomGap.SMALL)
        }
      }

      if (starterSettings.testFrameworks.size > 1) {
        row(JavaStartersBundle.message("title.project.test.framework.label")) {
          segmentedButton(starterSettings.testFrameworks, StarterTestRunner::title)
            .bind(testFrameworkProperty)

          bottomGap(BottomGap.SMALL)
        }
      }

      addGroupArtifactUi()
      addSdkUi()
      addSampleCodeUi()

      addFieldsAfter(this)
    }.withVisualPadding(topField = true)
  }

  override fun validate(): Boolean {
    if (!validateFormFields(component, contentPanel, validatedTextComponents)) {
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
}