// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.starters.remote.wizard

import com.intellij.ide.IdeBundle
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.starters.remote.*
import com.intellij.ide.starters.shared.*
import com.intellij.ide.starters.shared.ui.LibrariesSearchTextField
import com.intellij.ide.starters.shared.ui.LibraryDescriptionPanel
import com.intellij.ide.starters.shared.ui.SelectedLibrariesPanel
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractWizard
import com.intellij.ide.wizard.withVisualPadding
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import com.intellij.util.ModalityUiUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.DEFAULT_HGAP
import com.intellij.util.ui.UIUtil.DEFAULT_VGAP
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagLayout
import java.util.concurrent.TimeUnit
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.DocumentEvent
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

open class WebStarterLibrariesStep(contextProvider: WebStarterContextProvider) : ModuleWizardStep() {
  protected val moduleBuilder: WebStarterModuleBuilder = contextProvider.moduleBuilder
  protected val wizardContext: WizardContext = contextProvider.wizardContext
  protected val starterContext: WebStarterContext = contextProvider.starterContext
  protected val starterSettings: StarterWizardSettings = contextProvider.settings
  protected val parentDisposable: Disposable = contextProvider.parentDisposable

  private val topLevelPanel: JPanel = BorderLayoutPanel()
  private val contentPanel: DialogPanel by lazy { createComponent() }

  private val librariesList: CheckboxTreeBase by lazy { createLibrariesList() }
  private val librariesSearchField: LibrariesSearchTextField by lazy { createLibrariesFilter() }
  private val libraryDescriptionPanel: LibraryDescriptionPanel by lazy { LibraryDescriptionPanel() }
  private val selectedLibrariesPanel: SelectedLibrariesPanel by lazy { createSelectedLibrariesPanel() }
  private val frameworkVersionsModel: DefaultComboBoxModel<WebStarterFrameworkVersion> = DefaultComboBoxModel()

  protected val propertyGraph: PropertyGraph = PropertyGraph()
  private val frameworkVersionProperty: GraphProperty<WebStarterFrameworkVersion?> = propertyGraph.graphProperty { null }
  private val selectedDependencies: MutableSet<WebStarterDependency> = mutableSetOf()

  private var currentSearchString: String = ""
  private val searchMergingUpdateQueue: MergingUpdateQueue by lazy {
    MergingUpdateQueue("SearchLibs_" + moduleBuilder.builderId, 250, true, topLevelPanel, parentDisposable)
  }

  override fun getComponent(): JComponent = topLevelPanel

  override fun getHelpId(): String? = moduleBuilder.getHelpId()

  override fun getPreferredFocusedComponent(): JComponent? = librariesSearchField

  override fun updateDataModel() {
    starterContext.frameworkVersion = frameworkVersionProperty.get()
    starterContext.dependencies.clear()
    starterContext.dependencies.addAll(selectedDependencies)
  }

  override fun onStepLeaving() {
    super.onStepLeaving()

    updateDataModel()
  }

  override fun _init() {
    super._init()

    if (topLevelPanel.componentCount == 0) {
      topLevelPanel.add(contentPanel, BorderLayout.CENTER)
    }

    if (topLevelPanel.isDisplayable && topLevelPanel.isShowing) {
      // called after unsuccessful validation of step
      return
    }

    // libraries list may depend on options specified on the first step
    loadLibrariesList()
    loadFrameworkVersions()

    updateAvailableDependencies()
    getLibrariesRoot()?.let {
      selectFirstDependency(it)
    }
  }

  final override fun validate(): Boolean {
    val unavailable = selectedDependencies.filter { getDependencyState(it) is DependencyUnavailable }
    if (unavailable.isNotEmpty()) {
      val dependencyInfo = unavailable.joinToString { it.title }
      val version = frameworkVersionProperty.get()?.title ?: ""
      Messages.showErrorDialog(
        JavaStartersBundle.message("message.unavailable.dependencies", dependencyInfo, version),
        JavaStartersBundle.message("message.title.error"))
      return false
    }

    if (!validateFields()) {
      return false
    }

    if (starterContext.result == null) {
      // commit selected dependencies to starterContext
      updateDataModel()

      // try to validate and download result
      requestWebService()

      if (starterContext.result == null) {
        return false
      }
    }

    return true
  }

  protected open fun validateFields(): Boolean {
    return true
  }

  @RequiresBackgroundThread
  protected open fun validateWithServer(progressIndicator: ProgressIndicator): Boolean {
    return true
  }

  private fun requestWebService() {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        val progressIndicator = ProgressManager.getInstance().progressIndicator

        if (!validateWithServer(progressIndicator)) {
          return@runProcessWithProgressSynchronously
        }

        progressIndicator.checkCanceled()

        progressIndicator.text = JavaStartersBundle.message("message.state.downloading.template", moduleBuilder.presentableName)

        val downloadResult: DownloadResult? = try {
          moduleBuilder.downloadResultInternal(progressIndicator)
        }
        catch (e: Exception) {
          logger<WebStarterLibrariesStep>().info(e)

          EdtExecutorService.getScheduledExecutorInstance().schedule(
            {
              var message = JavaStartersBundle.message("error.text.with.error.content", e.message)
              message = StringUtil.shortenTextWithEllipsis(message, 1024, 0) // exactly 1024 because why not
              Messages.showErrorDialog(message, moduleBuilder.presentableName)
            },
            3, TimeUnit.SECONDS)

          null
        }

        starterContext.result = downloadResult
      }, JavaStartersBundle.message("message.state.preparing.template"), true, wizardContext.project)
  }

  private fun loadFrameworkVersions() {
    val availableFrameworkVersions = getAvailableFrameworkVersions()
    frameworkVersionsModel.removeAllElements()
    frameworkVersionsModel.addAll(availableFrameworkVersions)
    val defaultVersion = starterContext.frameworkVersion ?: availableFrameworkVersions.firstOrNull()
    if (availableFrameworkVersions.contains(defaultVersion)) {
      frameworkVersionProperty.set(defaultVersion)
    }
    else {
      frameworkVersionProperty.set(availableFrameworkVersions.firstOrNull())
    }
  }

  protected open fun addFieldsAfter(layout: LayoutBuilder) {}

  private fun createComponent(): DialogPanel {
    val messages = starterSettings.customizedMessages
    selectedLibrariesPanel.emptyText.text = messages?.noDependenciesSelectedLabel
                                            ?: JavaStartersBundle.message("hint.dependencies.not.selected")

    return panel(LCFlags.fillX, LCFlags.fillY) {
      val frameworkVersions = getAvailableFrameworkVersions()
      if (frameworkVersions.isNotEmpty()) {
        row {
          cell(isFullWidth = true) {
            label(messages?.frameworkVersionLabel ?: JavaStartersBundle.message("title.project.version.label"))

            if (frameworkVersions.size == 1) {
              label(frameworkVersions[0].title)
            }
            else {
              frameworkVersionsModel.addListDataListener(object : ListDataListener {
                override fun intervalAdded(e: ListDataEvent?) = updateAvailableDependencies()
                override fun intervalRemoved(e: ListDataEvent?) = updateAvailableDependencies()
                override fun contentsChanged(e: ListDataEvent?) = updateAvailableDependencies()
              })
              comboBox(frameworkVersionsModel, frameworkVersionProperty, SimpleListCellRenderer.create("") { it?.title ?: "" })
            }
          }
        }.largeGapAfter()
      }

      addFieldsAfter(this)

      row {
        label(messages?.dependenciesLabel ?: JavaStartersBundle.message("title.project.dependencies.label"))
      }

      row {
        component(JPanel(GridBagLayout()).apply {
          add(BorderLayoutPanel().apply {
            preferredSize = Dimension(0, 0)

            addToTop(librariesSearchField)
            addToCenter(ScrollPaneFactory.createScrollPane(librariesList))
          }, gridConstraint(0, 0))

          add(JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.emptyLeft(DEFAULT_HGAP * 2)
            preferredSize = Dimension(0, 0)

            add(libraryDescriptionPanel.apply {
              preferredSize = Dimension(0, 0)
            }, gridConstraint(0, 0))
            add(BorderLayoutPanel().apply {
              preferredSize = Dimension(0, 0)

              addToTop(JBLabel(messages?.selectedDependenciesLabel
                               ?: JavaStartersBundle.message("title.project.dependencies.selected.label")).apply {
                border = JBUI.Borders.empty(0, 0, DEFAULT_VGAP * 2, 0)
              })
              addToCenter(selectedLibrariesPanel)
            }, gridConstraint(0, 1))
          }, gridConstraint(1, 0))
        }).constraints(push, grow)
      }
    }.withVisualPadding()
  }

  private fun getAvailableFrameworkVersions(): List<WebStarterFrameworkVersion> {
    return starterContext.serverOptions.frameworkVersions.filter {
      moduleBuilder.isVersionAvailableInternal(it)
    }
  }

  private fun createLibrariesList(): CheckboxTreeBase {
    val list = CheckboxTreeBase(object : CheckboxTree.CheckboxTreeCellRenderer() {
      override fun customizeRenderer(
        tree: JTree?,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
      ) {
        if (value !is DefaultMutableTreeNode) return

        this.border = JBUI.Borders.empty(2, 0)
        when (val item = value.userObject) {
          is WebStarterDependencyCategory -> textRenderer.append(item.title)
          is WebStarterDependency -> {
            val enabled = (value as CheckedTreeNode).isEnabled
            val attributes = if (enabled) SimpleTextAttributes.REGULAR_ATTRIBUTES else SimpleTextAttributes.GRAYED_ATTRIBUTES
            textRenderer.append(item.title, attributes)
          }
        }
      }
    }, null)
    list.emptyText.text = IdeBundle.message("empty.text.nothing.found")

    enableEnterKeyHandling(list)

    list.rowHeight = 0
    list.isRootVisible = false
    list.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

    list.addCheckboxTreeListener(object : CheckboxTreeListener {
      override fun nodeStateChanged(node: CheckedTreeNode) {
        val dependency = node.userObject as? WebStarterDependency ?: return
        if (node.isChecked) {
          selectedDependencies.add(dependency)
        }
        else {
          selectedDependencies.remove(dependency)
        }
        librariesList.repaint()
        selectedLibrariesPanel.update(selectedDependencies)
      }
    })
    list.selectionModel.addTreeSelectionListener(TreeSelectionListener { e ->
      val path = e.path
      if (path != null && e.isAddedPath) {
        when (val item = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject) {
          is WebStarterDependency -> {
            updateSelectedLibraryInfo(item)
          }
          is WebStarterDependencyCategory -> libraryDescriptionPanel.update(item.title, null)
        }
      }
      else {
        libraryDescriptionPanel.reset()
      }
    })
    librariesSearchField.list = list

    return list
  }

  private fun isDependencyMatched(item: WebStarterDependency, search: String): Boolean {
    return item.title.contains(search, true)
           || (item.description ?: "").contains(search, true)
           || item.id.contains(search, true)
  }

  private fun createLibrariesFilter(): LibrariesSearchTextField {
    val textField = LibrariesSearchTextField()
    textField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        searchMergingUpdateQueue.queue(Update.create("", Runnable {
          ModalityUiUtil.invokeLaterIfNeeded(getModalityState(), Runnable {
            currentSearchString = textField.text
            loadLibrariesList()
            librariesList.repaint()
          })
        }))
      }
    })
    return textField
  }

  protected fun getModalityState(): ModalityState {
    return ModalityState.stateForComponent(wizardContext.getUserData(AbstractWizard.KEY)!!.contentComponent)
  }

  protected fun getDisposed(): Condition<Any> = Condition<Any> { Disposer.isDisposed(parentDisposable) }

  private fun createSelectedLibrariesPanel(): SelectedLibrariesPanel {
    val panel = SelectedLibrariesPanel()
    val messages = starterSettings.customizedMessages
    panel.emptyText.text = messages?.noDependenciesSelectedLabel ?: JavaStartersBundle.message("hint.dependencies.not.selected")
    panel.libraryRemoveListener = { libraryInfo ->
      selectedDependencies.remove(libraryInfo)
      walkCheckedTree(getLibrariesRoot()) {
        if (it.userObject == libraryInfo) {
          librariesList.setNodeState(it, false)
        }
      }
      selectedLibrariesPanel.update(selectedDependencies)
    }
    if (starterContext.frameworkVersion != null) {
      panel.dependencyStateFunction = { libraryInfo ->
        getDependencyState(libraryInfo)
      }
    }

    return panel
  }

  private fun getLibrariesRoot(): CheckedTreeNode? {
    return librariesList.model.root as? CheckedTreeNode
  }

  private fun getDependencyState(libraryInfo: LibraryInfo): DependencyState {
    val frameworkVersion = frameworkVersionProperty.get() ?: return DependencyAvailable
    return moduleBuilder.getDependencyStateInternal(frameworkVersion, libraryInfo as WebStarterDependency)
  }

  private fun loadLibrariesList() {
    val librariesRoot = CheckedTreeNode()
    val search = currentSearchString.trim()

    val dependencyCategories = starterContext.serverOptions.dependencyCategories
    for (category in dependencyCategories) {
      if (!category.isAvailable(starterContext)) continue

      val categoryNode = DefaultMutableTreeNode(category, true)

      for (dependency in category.dependencies) {
        if (search.isBlank() || isDependencyMatched(dependency, search)) {
          val libraryNode = CheckedTreeNode(dependency)
          if (dependency.isDefault) {
            selectedDependencies.add(dependency)
          }

          libraryNode.isChecked = selectedDependencies.contains(dependency)

          if (dependency.isDefault) {
            libraryNode.isEnabled = false
          }
          else {
            val state = getDependencyState(dependency)
            libraryNode.isEnabled = state is DependencyAvailable
          }

          if (dependencyCategories.size > 1) {
            categoryNode.add(libraryNode)
          }
          else {
            librariesRoot.add(libraryNode)
          }
        }
      }

      if (dependencyCategories.size > 1) {
        if (categoryNode.childCount > 0) {
          librariesRoot.add(categoryNode)
        }
      }
    }
    librariesList.model = DefaultTreeModel(librariesRoot)

    if (search.isNotBlank()) {
      for (category in librariesRoot.children()) {
        librariesList.expandPath(TreeUtil.getPath(librariesRoot, category))
      }
      selectFirstDependency(librariesRoot)
    }
  }

  private fun selectFirstDependency(librariesRoot: CheckedTreeNode) {
    if (librariesRoot.childCount > 0) {
      val firstNode = librariesRoot.getChildAt(0)
      if (firstNode is CheckedTreeNode) {
        librariesList.selectionModel.addSelectionPath(TreeUtil.getPath(librariesRoot, firstNode))
      }
      else {
        librariesList.expandPath(TreeUtil.getPath(librariesRoot, firstNode))
        if (firstNode.childCount > 0) {
          librariesList.selectionModel.addSelectionPath(TreeUtil.getPath(librariesRoot, firstNode.getChildAt(0)))
        }
      }
    }
  }

  private fun updateSelectedLibraryInfo(item: WebStarterDependency) {
    val dependencyState = getDependencyState(item)
    val versionInfo = if (dependencyState is DependencyUnavailable) dependencyState.hint else null
    libraryDescriptionPanel.update(item, versionInfo)
  }

  private fun updateAvailableDependencies() {
    selectedLibrariesPanel.update(selectedDependencies)

    val root = getLibrariesRoot() ?: return

    walkCheckedTree(root) {
      val dependency = it.userObject as? WebStarterDependency
      if (dependency != null) {
        val state = getDependencyState(dependency)
        it.isEnabled = state is DependencyAvailable
      }
    }
    librariesList.repaint()

    val selectedDependency = (librariesList.selectionPath?.lastPathComponent as? CheckedTreeNode)?.userObject
    if (selectedDependency is WebStarterDependency) {
      updateSelectedLibraryInfo(selectedDependency)
    }
  }
}
