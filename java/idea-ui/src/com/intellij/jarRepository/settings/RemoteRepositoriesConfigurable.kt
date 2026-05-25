// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository.settings

import com.intellij.ide.JavaUiBundle
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.jarRepository.services.MavenRepositoryServicesManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ListUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import org.jetbrains.annotations.Nls
import java.net.MalformedURLException
import java.net.URL
import java.util.UUID
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.ListSelectionModel

class RemoteRepositoriesConfigurable(private val project: Project) : SearchableConfigurable, Configurable.NoScroll {

  private val servicesModel = CollectionListModel<String>()
  private val reposModel = CollectionListModel<RemoteRepositoryDescription>()

  private val jarRepositoryList = JBList<RemoteRepositoryDescription>()
  private val serviceList = JBList<String>()

  private val addRepoButton = mnemonicButton(JavaUiBundle.message("button.add2")).apply { isFocusable = false }
  private val editRepoButton = mnemonicButton(JavaUiBundle.message("button.edit2"))
  private val removeRepoButton = mnemonicButton(JavaUiBundle.message("button.remove2"))
  private val resetToDefaultReposButton = mnemonicButton(JavaUiBundle.message("button.reset.defaults"))

  private val addServiceButton = mnemonicButton(JavaUiBundle.message("button.add")).apply { isFocusable = false }
  private val editServiceButton = mnemonicButton(JavaUiBundle.message("button.edit"))
  private val removeServiceButton = mnemonicButton(JavaUiBundle.message("button.remove"))
  private val testServiceButton = mnemonicButton(JavaUiBundle.message("button.test"))
  private val resetToDefaultServicesButton = mnemonicButton(JavaUiBundle.message("button.reset.defaults"))

  private val workspaceModel: WorkspaceModel = WorkspaceModel.getInstance(project)
  private var mutableEntityStorage: MutableEntityStorage = MutableEntityStorage.from(workspaceModel.currentSnapshot)

  private val mainPanel: JComponent = buildMainPanel()

  init {
    configControls()
  }

  override fun getDisplayName(): String = JavaUiBundle.message("configurable.RemoteRepositoriesConfigurable.display.name")

  override fun getHelpTopic(): String = "reference.jar.repositories"

  override fun getId(): String = javaClass.name

  override fun createComponent(): JComponent = mainPanel

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun isModified(): Boolean {
    return isServiceListModified() ||
           isRepoListModified() ||
           (mutableEntityStorage as MutableEntityStorageInstrumentation).hasChanges()
  }

  private fun isServiceListModified(): Boolean {
    return servicesModel.items != MavenRepositoryServicesManager.getInstance(project).urls
  }

  private fun isRepoListModified(): Boolean {
    val repos = RemoteRepositoriesConfiguration.getInstance(project).repositories
    return reposModel.items != repos
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun apply() {
    val newUrls = reposModel.items.map { it.url }
    val oldUrls = RemoteRepositoriesConfiguration.getInstance(project).repositories.map { it.url }
    MavenRepositoryServicesManager.getInstance(project).urls = servicesModel.items
    RemoteRepositoriesConfiguration.getInstance(project).repositories = reposModel.items
    applyMutableEntityStorageChanges()

    if (!newUrls.containsAll(oldUrls) || (mutableEntityStorage as MutableEntityStorageInstrumentation).hasChanges()) {
      reloadAllRepositoryLibraries(project)
    }

    resetMutableEntityStorage()
  }

  override fun reset() {
    resetServicesModel(MavenRepositoryServicesManager.getInstance(project).urls)
    resetReposModel(RemoteRepositoriesConfiguration.getInstance(project).repositories)
    resetMutableEntityStorage()
  }

  private fun resetServicesModel(urls: List<String>) {
    servicesModel.removeAll()
    servicesModel.add(urls)
  }

  private fun resetReposModel(repositories: List<RemoteRepositoryDescription>) {
    reposModel.replaceAll(repositories)
  }

  private fun resetMutableEntityStorage() {
    mutableEntityStorage = MutableEntityStorage.from(workspaceModel.currentSnapshot)
  }

  private fun applyMutableEntityStorageChanges() {
    try {
      WriteAction.run<Throwable> {
        workspaceModel.updateProjectModel(
          "Update libraries bindings to remote repositories on repository remove"
        ) {
          it.applyChangesFrom(mutableEntityStorage)
        }
      }
    }
    catch (e: Throwable) {
      throw RuntimeException(e)
    }
  }

  private fun buildMainPanel(): JComponent {
    return panel {
      row(JavaUiBundle.message("settings.remote.repo.maven.jar.repositories")) {}
      row {
        scrollCell(jarRepositoryList).align(Align.FILL).resizableColumn()
        cell(buildButtonsColumn(addRepoButton,
                                editRepoButton,
                                removeRepoButton,
                                resetButton = resetToDefaultReposButton)).align(Align.FILL)
      }.resizableRow()

      row(JavaUiBundle.message("settings.remote.repo.artifactory.or.nexus.service.urls")) {}
      row {
        scrollCell(serviceList).align(Align.FILL).resizableColumn()
        cell(buildButtonsColumn(addServiceButton, editServiceButton, removeServiceButton, testServiceButton,
                                resetButton = resetToDefaultServicesButton)).align(Align.FILL)
      }
    }
  }

  private fun buildButtonsColumn(vararg topButtons: JButton, resetButton: JButton): JComponent {
    return panel {
      topButtons.forEach { btn ->
        row { cell(btn).align(AlignX.FILL) }
      }
      row { cell(JLabel()) }.resizableRow() // Filler between Reset and other buttons.
      row { cell(resetButton).align(AlignX.FILL) }
    }
  }

  private fun configControls() {
    setupCommonListControls(
      serviceList, servicesModel, addServiceButton, editServiceButton,
      JavaUiBundle.message("settings.remote.repo.artifactory.or.nexus"),
      JavaUiBundle.message("settings.remote.repo.service.url"),
      JavaUiBundle.message("settings.remote.repo.no.services"),
      DataAdapter.STRING_ADAPTER,
    )
    ListUtil.addRemoveListener(removeServiceButton, serviceList)

    setupCommonListControls(
      jarRepositoryList, reposModel, addRepoButton, editRepoButton,
      JavaUiBundle.message("settings.remote.repo.maven.repository.url"),
      JavaUiBundle.message("settings.remote.repo.Maven.Repository.URL"),
      JavaUiBundle.message("settings.remote.repo.no.remote.repositories"),
      DataAdapter.REPOSITORY_DESCRIPTION_ADAPTER,
    )
    setupRepoRemoveButton()

    ListUtil.disableWhenNoSelection(testServiceButton, serviceList)
    testServiceButton.addActionListener {
      val value = serviceList.selectedValue
      if (!value.isNullOrEmpty()) {
        testServiceButton.isEnabled = false
        JarRepositoryManager.searchRepositories(project, listOf(value)) { infos ->
          testServiceButton.isEnabled = true
          if (infos.isEmpty()) {
            Messages.showMessageDialog(
              JavaUiBundle.message("settings.remote.repo.no.repositories.found"),
              JavaUiBundle.message("settings.remote.repo.service.connection.failed"),
              Messages.getWarningIcon(),
            )
          }
          else {
            Messages.showMessageDialog(
              JavaUiBundle.message("settings.remote.repo.repositories.found", infos.size),
              JavaUiBundle.message("settings.remote.repo.service.connection.successful"),
              Messages.getInformationIcon(),
            )
          }
          true
        }
      }
    }

    resetToDefaultReposButton.addActionListener {
      val currentIds = reposModel.items.mapTo(HashSet()) { it.id }
      val bindLibrariesCount = countBindLibraries(mutableEntityStorage, currentIds)
      if (bindLibrariesCount == 0) {
        resetReposModel(RemoteRepositoryDescription.DEFAULT_REPOSITORIES)
        return@addActionListener
      }

      val resetConfirmed = MessageDialogBuilder.yesNo(
        JavaUiBundle.message("jar.repository.manager.confirm.reset.default.repositories.dialog.title"),
        JavaUiBundle.message("jar.repository.manager.confirm.reset.default.repositories.dialog.text", bindLibrariesCount),
      ).ask(project)

      if (resetConfirmed) {
        updateLibrariesRepositoryId(mutableEntityStorage, currentIds, null)
        resetReposModel(RemoteRepositoryDescription.DEFAULT_REPOSITORIES)
      }
    }

    resetToDefaultServicesButton.addActionListener {
      resetServicesModel(MavenRepositoryServicesManager.DEFAULT_SERVICES)
    }
  }

  private fun setupRepoRemoveButton() {
    removeRepoButton.addActionListener {
      val index = jarRepositoryList.selectedIndex
      if (index < 0 || index > jarRepositoryList.itemsCount) {
        return@addActionListener
      }
      val remoteRepository = reposModel.getElementAt(index)

      val bindLibrariesCount = countBindLibraries(mutableEntityStorage, remoteRepository)
      if (bindLibrariesCount > 0) {
        val dialog = RepositoryRemoveDialog(project, remoteRepository, reposModel.items, bindLibrariesCount)
        if (!dialog.showAndGet()) {
          return@addActionListener
        }
        updateLibrariesRepositoryId(mutableEntityStorage, remoteRepository, dialog.getSelectedRepository())
      }

      ListUtil.removeSelectedItems(jarRepositoryList)
      jarRepositoryList.requestFocusInWindow()
    }
  }

  private fun <T : Any> setupCommonListControls(
    list: JBList<T>,
    model: CollectionListModel<T>,
    addButton: JButton,
    editButton: JButton,
    modificationDialogTitle: @NlsContexts.DialogMessage String,
    modificationDialogHint: String,
    emptyListHint: @NlsContexts.StatusText String,
    adapter: DataAdapter<T, String>,
  ) {
    list.model = model
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.cellRenderer = textListCellRenderer { item -> item?.let(adapter::toPresentation) ?: "" }
    addButton.addActionListener {
      val value = list.selectedValue
      val initialValue = if (value == null) "https://" else adapter.toPresentation(value)
      val text = Messages.showInputDialog(
        modificationDialogTitle, JavaUiBundle.message("dialog.title.add.repository.0", modificationDialogHint),
        Messages.getQuestionIcon(), initialValue, URLInputValidator(),
      )
      if (!text.isNullOrEmpty()) {
        model.add(adapter.create(text))
        @Suppress("UNCHECKED_CAST")
        (list as JBList<Any>).setSelectedValue(text, true)
      }
    }
    editButton.addActionListener {
      val index = list.selectedIndex
      val element = model.getElementAt(index)!!
      val text = Messages.showInputDialog(
        modificationDialogTitle, JavaUiBundle.message("dialog.title.edit.repository.0", modificationDialogHint),
        Messages.getQuestionIcon(), adapter.toPresentation(element), URLInputValidator(),
      )
      if (!text.isNullOrEmpty()) {
        model.setElementAt(adapter.change(element, text), index)
      }
    }
    ListUtil.disableWhenNoSelection(editButton, list)
    list.emptyText.text = emptyListHint
  }

  private interface DataAdapter<Data : Any, Presentation : Any> {
    fun toPresentation(data: Data): Presentation
    fun create(presentation: Presentation): Data
    fun change(current: Data, changes: Presentation): Data

    companion object {
      val STRING_ADAPTER: DataAdapter<String, String> = object : DataAdapter<String, String> {
        override fun toPresentation(data: String): String = data
        override fun create(presentation: String): String = presentation
        override fun change(current: String, changes: String): String = changes
      }

      val REPOSITORY_DESCRIPTION_ADAPTER: DataAdapter<RemoteRepositoryDescription, String> =
        object : DataAdapter<RemoteRepositoryDescription, String> {
          override fun toPresentation(data: RemoteRepositoryDescription): String = data.url
          override fun create(presentation: String): RemoteRepositoryDescription {
            val uuid = UUID.randomUUID()
            return RemoteRepositoryDescription(uuid.toString(), uuid.toString(), presentation)
          }

          override fun change(current: RemoteRepositoryDescription, changes: String): RemoteRepositoryDescription {
            return RemoteRepositoryDescription(current.id, current.name, changes)
          }
        }
    }
  }

  private class URLInputValidator : InputValidator {
    override fun checkInput(inputString: String): Boolean {
      return try {
        val url = URL(inputString)
        !url.host.isNullOrEmpty() || "file" == url.protocol
      }
      catch (_: MalformedURLException) {
        false
      }
    }

    override fun canClose(inputString: String): Boolean = checkInput(inputString)
  }
}

private fun mnemonicButton(@Nls text: String): JButton {
  val button = JButton()
  val parsed = TextWithMnemonic.parse(text)
  button.text = parsed.text
  if (parsed.hasMnemonic()) {
    button.mnemonic = parsed.mnemonicCode
    button.displayedMnemonicIndex = parsed.mnemonicIndex
  }
  return button
}
