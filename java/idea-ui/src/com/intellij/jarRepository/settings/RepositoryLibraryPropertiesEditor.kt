// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository.settings

import com.intellij.CommonBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.jarRepository.RepositoryLibrarySettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel
import java.awt.CardLayout
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class RepositoryLibraryPropertiesEditor(
  project: Project?,
  propertiesModel: RepositoryLibraryPropertiesModel,
  description: RepositoryLibraryDescription,
  allowExcludingTransitiveDependencies: Boolean,
  externalOnChangeListener: ModelChangeListener,
  globalLibrary: Boolean,
) {
  constructor(
    project: Project?,
    model: RepositoryLibraryPropertiesModel,
    description: RepositoryLibraryDescription,
    globalLibrary: Boolean,
  ) :
    this(project, model, description, true, ModelChangeListener { }, globalLibrary)

  fun interface ModelChangeListener {
    fun onChange(editor: RepositoryLibraryPropertiesEditor)
  }

  private val project: Project = project ?: ProjectManager.getInstance().defaultProject
  private val model: RepositoryLibraryPropertiesModel = propertiesModel
  private val repositoryLibraryDescription: RepositoryLibraryDescription = description
  private val initialModel: RepositoryLibraryPropertiesModel = propertiesModel.clone()

  private var currentState: State = State.Loading
  private var versions: List<String> = emptyList()

  private val versionSelector: ComboBox<VersionItem> = ComboBox<VersionItem>().also {
    it.renderer = textListCellRenderer("") { item -> item?.displayName ?: "" }
  }

  private val downloadSourcesCheckBox: JBCheckBox = checkBoxWithMnemonic(JavaUiBundle.message("checkbox.download.sources"))
  private val downloadJavaDocsCheckBox: JBCheckBox = checkBoxWithMnemonic(JavaUiBundle.message("checkbox.download.javadocs2"))
  private val downloadAnnotationsCheckBox: JBCheckBox = checkBoxWithMnemonic(JavaUiBundle.message("checkbox.download.annotations"))

  private val mavenCoordinates: JBLabel = JBLabel().apply {
    isOpaque = false
    isRequestFocusEnabled = true
    text = ""
    setCopyable(true)
  }

  private val myIncludeTransitiveDepsCheckBox: ThreeStateCheckBox = ThreeStateCheckBox(
    UIUtil.replaceMnemonicAmpersand(JavaUiBundle.message("repository.library.properties.include.transitive.dependencies"))
  ).apply { isThirdStateEnabled = false }

  private val myManageDependenciesLink: ActionLink = ActionLink(CommonBundle.message("action.text.configure.ellipsis")) {
    configureTransitiveDependencies()
  }.apply { border = JBUI.Borders.emptyLeft(10) }

  private val myReloadButton: JButton = JButton(JavaUiBundle.message("button.reload")).also {
    it.addActionListener { reloadVersionsAsync() }
  }

  private val myRemoteRepositoryComboBox: ComboBox<RemoteRepositoryDescription> = run {
    val combo = ComboBox<RemoteRepositoryDescription>()
    combo.renderer = textListCellRenderer(JavaUiBundle.message("repository.library.bind.repository.not.selected")) { it?.url ?: "" }
    @Suppress("UNCHECKED_CAST")
    combo.model = propertiesModel.remoteRepositoryModel as CollectionComboBoxModel<RemoteRepositoryDescription>
    combo.addActionListener { reloadVersionsAsync() }
    combo
  }

  private val myPropertiesPanel: JPanel = JPanel(CardLayout(0, 0))

  private val onChangeListener: ModelChangeListener = ModelChangeListener { editor ->
    externalOnChangeListener.onChange(editor)
    mavenCoordinates.text = repositoryLibraryDescription.getMavenCoordinates(model.version)
  }

  private val mainPanel: JPanel

  init {
    myPropertiesPanel.add(buildLoadedCard(allowExcludingTransitiveDependencies), State.Loaded.name)
    myPropertiesPanel.add(buildLoadingCard(), State.Loading.name)
    myPropertiesPanel.add(buildFailedToLoadCard(), State.FailedToLoad.name)

    val remoteRepoVisible = !globalLibrary &&
                            RepositoryLibrarySettings.getInstanceOrDefaults(this.project).isBindJarRepositoryUiSettingsDisplayed()

    mainPanel = panel {
      row(JavaUiBundle.message("label.maven")) {
        cell(mavenCoordinates).align(AlignX.LEFT)
      }
      val remoteRepoRows = rowsRange {
        row { cell(myRemoteRepositoryComboBox).align(AlignX.FILL) }
        row {
          cell(JBLabel().apply {
            text = UIUtil.replaceMnemonicAmpersand(JavaUiBundle.message("label.bind.remote.repository"))
            componentStyle = UIUtil.ComponentStyle.SMALL
            fontColor = UIUtil.FontColor.BRIGHTER
            labelFor = myRemoteRepositoryComboBox
          })
        }
      }
      remoteRepoRows.visible(remoteRepoVisible)
      row {
        cell(myPropertiesPanel).align(Align.FILL)
      }
    }

    updateManageDependenciesLink()
    reloadVersionsAsync()
  }

  private fun buildLoadedCard(allowExcludingTransitiveDependencies: Boolean): JComponent {
    return panel {
      row(UIUtil.replaceMnemonicAmpersand(JavaUiBundle.message("label.version"))) {
        cell(versionSelector)
      }
      row { cell(downloadSourcesCheckBox) }
      row { cell(downloadJavaDocsCheckBox) }
      row { cell(downloadAnnotationsCheckBox) }
      row {
        cell(myIncludeTransitiveDepsCheckBox)
        cell(myManageDependenciesLink)
      }.visible(allowExcludingTransitiveDependencies)
    }
  }

  private fun buildLoadingCard(): JComponent {
    return panel {
      row {
        label(UIUtil.replaceMnemonicAmpersand(JavaUiBundle.message("label.loading.available.versions")))
          .applyToComponent { labelFor = versionSelector }
      }
    }
  }

  private fun buildFailedToLoadCard(): JComponent {
    return panel {
      row {
        label(JavaUiBundle.message("label.failed.to.load.versions"))
        cell(myReloadButton)
      }
    }
  }

  private fun configureTransitiveDependencies() {
    val selectedVersion = getSelectedVersion()
    LOG.assertTrue(selectedVersion != null)

    val root = JarRepositoryManager.loadDependenciesTree(repositoryLibraryDescription, selectedVersion!!, project) ?: return

    val dependencies = DependencyExclusionEditor(root, mainPanel).selectExcludedDependencies(model.excludedDependencies)
    if (dependencies != null) {
      model.setExcludedDependencies(dependencies)
      updateIncludeTransitiveDepsCheckBoxState()
      onChangeListener.onChange(this)
    }
  }

  private fun toVersionItem(version: String?): VersionItem? {
    return when {
      version.isNullOrEmpty() -> null
      version == RepositoryLibraryDescription.ReleaseVersionId -> VersionItem.LatestRelease
      version == RepositoryLibraryDescription.LatestVersionId -> VersionItem.LatestVersion
      else -> VersionItem.ExactVersion(version)
    }
  }

  private fun setState(state: State) {
    currentState = state
    (myPropertiesPanel.layout as CardLayout).show(myPropertiesPanel, state.name)
    onChangeListener.onChange(this)
  }

  private fun reloadVersionsAsync() {
    setState(State.Loading)
    val selectedRemoteRepository = model.remoteRepository
    val promise = if (selectedRemoteRepository != null) {
      JarRepositoryManager.getAvailableVersions(project, repositoryLibraryDescription, listOf(selectedRemoteRepository))
    }
    else {
      JarRepositoryManager.getAvailableVersions(project, repositoryLibraryDescription)
    }
    promise.onSuccess { result -> versionsLoaded(ArrayList(result)) }
  }

  private fun initVersionsPanel() {
    val versionSelectorModel = CollectionComboBoxModel<VersionItem>()
    versionSelectorModel.add(VersionItem.LatestRelease)
    versionSelectorModel.add(VersionItem.LatestVersion)
    versionSelectorModel.add(versions.map { VersionItem.ExactVersion(it) })
    versionSelector.model = versionSelectorModel
    versionSelector.selectedItem = toVersionItem(model.version)
    setState(State.Loaded)
    versionSelector.addItemListener {
      model.version = getSelectedVersion()
      onChangeListener.onChange(this)
      updateManageDependenciesLink()
    }
    downloadSourcesCheckBox.isSelected = model.isDownloadSources
    downloadSourcesCheckBox.addChangeListener {
      model.isDownloadSources = downloadSourcesCheckBox.isSelected
      onChangeListener.onChange(this)
    }
    downloadJavaDocsCheckBox.isSelected = model.isDownloadJavaDocs
    downloadJavaDocsCheckBox.addChangeListener {
      model.isDownloadJavaDocs = downloadJavaDocsCheckBox.isSelected
      onChangeListener.onChange(this)
    }
    downloadAnnotationsCheckBox.isSelected = model.isDownloadAnnotations
    downloadAnnotationsCheckBox.addChangeListener {
      model.isDownloadAnnotations = downloadAnnotationsCheckBox.isSelected
      onChangeListener.onChange(this)
    }

    updateIncludeTransitiveDepsCheckBoxState()
    myIncludeTransitiveDepsCheckBox.addChangeListener {
      updateManageDependenciesLink()
      val state = myIncludeTransitiveDepsCheckBox.state
      if (state != ThreeStateCheckBox.State.DONT_CARE) {
        model.setExcludedDependencies(emptyList())
      }
      model.isIncludeTransitiveDependencies = state != ThreeStateCheckBox.State.NOT_SELECTED
      onChangeListener.onChange(this)
    }
    updateManageDependenciesLink()
  }

  private fun updateIncludeTransitiveDepsCheckBoxState() {
    myIncludeTransitiveDepsCheckBox.state = when {
      !model.isIncludeTransitiveDependencies -> ThreeStateCheckBox.State.NOT_SELECTED
      model.excludedDependencies.isEmpty() -> ThreeStateCheckBox.State.SELECTED
      else -> ThreeStateCheckBox.State.DONT_CARE
    }
  }

  private fun updateManageDependenciesLink() {
    val enable = myIncludeTransitiveDepsCheckBox.state != ThreeStateCheckBox.State.NOT_SELECTED && getSelectedVersion() != null
    myManageDependenciesLink.isEnabled = enable
  }

  private fun versionsLoaded(versions: List<String>) {
    this.versions = versions
    if (versions.isEmpty()) {
      versionsFailedToLoad()
      return
    }
    ApplicationManager.getApplication().invokeLater({ initVersionsPanel() }, ModalityState.any())
  }

  private fun versionsFailedToLoad() {
    ApplicationManager.getApplication().invokeLater({ setState(State.FailedToLoad) }, ModalityState.any())
  }

  fun getSelectedVersion(): String? {
    val selectedItem = versionSelector.selectedItem as? VersionItem
    return selectedItem?.versionId
  }

  fun getMainPanel(): JPanel = mainPanel

  fun isValid(): Boolean = currentState == State.Loaded

  fun hasChanges(): Boolean = model != initialModel

  private enum class State {
    Loading,
    FailedToLoad,
    Loaded
  }

  companion object {
    private val LOG = logger<RepositoryLibraryPropertiesEditor>()
  }
}

private fun checkBoxWithMnemonic(@Nls text: String): JBCheckBox {
  val checkBox = JBCheckBox()
  applyMnemonicText(checkBox, text)
  return checkBox
}

private fun applyMnemonicText(button: AbstractButton, @Nls text: String) {
  val parsed = TextWithMnemonic.parse(text)
  button.text = parsed.text
  if (parsed.hasMnemonic()) {
    button.mnemonic = parsed.mnemonicCode
    button.displayedMnemonicIndex = parsed.mnemonicIndex
  }
}
