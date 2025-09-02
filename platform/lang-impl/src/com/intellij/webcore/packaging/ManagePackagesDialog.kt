// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webcore.packaging

import com.intellij.CommonBundle
import com.intellij.execution.ExecutionException
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.CollectionListModel
import com.intellij.ui.FilterComponent
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.CatchingConsumer
import com.intellij.util.Function
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.PlatformColors
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.UiNotifyConnector
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.RenderingHints
import java.awt.event.ActionListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.font.FontRenderContext
import java.awt.geom.Rectangle2D
import java.io.IOException
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

/**
 * User: catherine
 *
 *
 * UI for installing python packages
 */
open class ManagePackagesDialog @JvmOverloads constructor(
  private val myProject: Project,
  private val myController: PackageManagementService,
  private val myPackageListener: PackageManagementService.Listener?,
  notificationPanel: PackagesNotificationPanel = PackagesNotificationPanel(),
)
  : DialogWrapper(myProject, true) {

  private var myFilter = MyPackageFilter()
  private var myDescriptionTextArea = SwingHelper.createHtmlViewer(true, null, null, null).apply {
    border = IdeBorderFactory.createBorder(SideBorder.TOP)
  }
  private val myPackages: JBList<RepoPackage> = JBList<RepoPackage>()
  private val myInstallButton: JButton = JButton(LangBundle.message("button.install.package"))
  private val myOptionsCheckBox: JCheckBox = JCheckBox(LangBundle.message("checkbox.package.options"))
  private val myOptionsField: JTextField = JTextField()
  private val myInstallToUser: JCheckBox = JCheckBox()
  private val myVersionComboBox = ComboBox<String>()
  private val myVersionCheckBox: JCheckBox = JCheckBox(LangBundle.message("checkbox.package.specify.version"))
  private val myManageButton: JButton = JButton(LangBundle.message("button.manage.repositories"))
  private val myNotificationArea = notificationPanel
  private val myNotificationsAreaPlaceholder = myNotificationArea.component

  private var myPackagesModel: PackagesModel? = null
  private var mySelectedPackageName: String? = null
  private val myInstalledPackages: MutableSet<String>

  private val myCurrentlyInstalling: MutableSet<String> = HashSet()
  private val myListSpeedSearch: ListSpeedSearch<*>

  private val myMainPanel: JPanel = createMainPanel()

  init {
    init()
    title = IdeBundle.message("available.packages.dialog.title")

    myListSpeedSearch = ListSpeedSearch.installOn(myPackages, Function { o: Any? ->
      if (o is RepoPackage) o.name else ""
    })

    myPackages.selectionMode = ListSelectionModel.SINGLE_SELECTION
    myPackages.addListSelectionListener(MyPackageSelectionListener())

    myInstallToUser.addActionListener(
      ActionListener { myController.installToUserChanged(myInstallToUser.isSelected) })
    myOptionsCheckBox.isEnabled = false
    myVersionCheckBox.isEnabled = false
    myVersionCheckBox.addActionListener(ActionListener { myVersionComboBox.isEnabled = myVersionCheckBox.isSelected })

    UiNotifyConnector.doWhenFirstShown(myPackages) { this.initModel() }
    myOptionsCheckBox.addActionListener(ActionListener { myOptionsField.isEnabled = myOptionsCheckBox.isSelected })
    myInstallButton.isEnabled = false
    myDescriptionTextArea.addHyperlinkListener(PluginManagerMain.MyHyperlinkListener())
    addInstallAction()
    myInstalledPackages = HashSet()
    updateInstalledPackages()
    addManageAction()
    myPackages.setCellRenderer(MyTableRenderer())
    calculateAndSetCellDimensions()

    myInstallToUser.isVisible = myController.canInstallToUser()
    if (myInstallToUser.isVisible) {
      myInstallToUser.isSelected = myController.isInstallToUserSelected
      myInstallToUser.text =  myController.installToUserText
    }
  }

  private fun calculateAndSetCellDimensions() {
    val font = myPackages.font
    val fontDimensions = getFontDimensions(font)

    myPackages.fixedCellHeight = fontDimensions.height.toInt() + myPackages.getFontMetrics(font).descent
    myPackages.fixedCellWidth = fontDimensions.width.toInt() * CHARACTERS_PER_CELL
  }

  private fun getFontDimensions(font: Font): Rectangle2D {
    val frc = FontRenderContext(
      null,
      RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
      RenderingHints.VALUE_FRACTIONALMETRICS_ON
    )
    return font.getMaxCharBounds(frc)
  }

  override fun getDimensionServiceKey() = this::class.java.name + ".DimensionServiceKey"

  private fun createMainPanel(): JPanel {
    val packagesToolbar = JPanel(BorderLayout()).apply {
      add(myFilter, BorderLayout.CENTER)
      add(ActionManager.getInstance().createActionToolbar("ManagePackagesDialog",
                                                          DefaultActionGroup(createPackagesReloadAction()),
                                                          true).apply {
        targetComponent = myPackages
        layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
      }.component, BorderLayout.LINE_END)
    }

    val packagesPanel = JPanel(BorderLayout()).apply {
      add(JBScrollPane(myPackages), BorderLayout.CENTER)
      add(packagesToolbar, BorderLayout.NORTH)
    }

    val detailsPanel = panel {
      row { label(IdeBundle.message("editbox.plugin.description")) }
      row { scrollCell(myDescriptionTextArea).align(Align.FILL) }.resizableRow()
      row { cell(myVersionCheckBox).widthGroup("A"); cell(myVersionComboBox).align(AlignX.FILL) }
      row { cell(myOptionsCheckBox).widthGroup("A"); cell(myOptionsField).align(AlignX.FILL) }
    }

    return panel {
      row {
        cell(OnePixelSplitter(false, "manage.packages.splitter.proportions", 0.5f).apply {
          firstComponent = packagesPanel
          secondComponent = detailsPanel
        }).resizableColumn().align(Align.FILL)
      }.resizableRow()

      row { cell(myInstallToUser) }
      row { cell(myNotificationsAreaPlaceholder).resizableColumn().align(Align.FILL) }
      separator()
      row {
        cell(JPanel()).resizableColumn()
        cell(myInstallButton).align(AlignX.RIGHT)
        cell(myManageButton).align(AlignX.RIGHT)
        cell(JButton(CommonBundle.getCloseButtonText()).apply {
          addActionListener { doOKAction() }
        }).align(AlignX.RIGHT)
      }
    }.apply {
      minimumSize = JBUI.size(300, 300)
      preferredSize = JBUI.size(800, 600)
    }
  }

  private fun createPackagesReloadAction(): AnAction {
    return DumbAwareAction.create(IdeBundle.message("action.AnActionButton.text.reload.list.of.packages"), AllIcons.Actions.Refresh) {
      myPackages.setPaintBusy(true)
      val application = ApplicationManager.getApplication()
      application.executeOnPooledThread {
        try {
          myController.reloadAllPackages()
          initModel()
          myPackages.setPaintBusy(false)
        }
        catch (e1: IOException) {
          application.invokeLater(Runnable {
            Messages.showErrorDialog(myMainPanel,
                                     IdeBundle.message("error.updating.package.list", e1.message),
                                     IdeBundle.message(
                                       "action.AnActionButton.text.reload.list.of.packages"))
            LOG.info("Error updating list of repository packages", e1)
            myPackages.setPaintBusy(false)
          }, ModalityState.any())
        }
      }
    }
  }

  fun selectPackage(pkg: InstalledPackage) {
    mySelectedPackageName = pkg.name
    doSelectPackage(mySelectedPackageName)
  }

  private fun addManageAction() {
    if (myController.canManageRepositories()) {
      myManageButton.addActionListener {
        ManageRepoDialog(myProject, myController).show()
      }
    }
    else {
      myManageButton.isVisible = false
    }
  }

  private fun addInstallAction() {
    myInstallButton.addActionListener {
      val pyPackage = myPackages.selectedValue
      if (pyPackage is RepoPackage) {
        var extraOptions: String? = null
        if (myOptionsCheckBox.isEnabled && myOptionsCheckBox.isSelected) {
          extraOptions = myOptionsField.text
        }

        var version: String? = null
        if (myVersionCheckBox.isEnabled && myVersionCheckBox.isSelected) {
          version = myVersionComboBox.selectedItem as String
        }

        val listener: PackageManagementService.Listener = object : PackageManagementService.Listener {
          override fun operationStarted(packageName: String) {
            if (!ApplicationManager.getApplication().isDispatchThread) {
              ApplicationManager.getApplication().invokeLater(
                { handleInstallationStarted(packageName) }, ModalityState.stateForComponent(
                (myMainPanel)))
            }
            else {
              handleInstallationStarted(packageName)
            }
          }

          override fun operationFinished(packageName: String,
                                         errorDescription: PackageManagementService.ErrorDescription?) {
            if (!ApplicationManager.getApplication().isDispatchThread) {
              ApplicationManager.getApplication().invokeLater(
                { handleInstallationFinished(packageName, errorDescription) }, ModalityState.stateForComponent(
                (myMainPanel)))
            }
            else {
              handleInstallationFinished(packageName, errorDescription)
            }
          }
        }
        myController.installPackage(pyPackage, version, false, extraOptions, listener, myInstallToUser.isSelected)
        myInstallButton.isEnabled = false
      }
      PackageManagementUsageCollector.triggerInstallPerformed(myProject, myController)
    }
  }

  private fun handleInstallationStarted(packageName: String) {
    myNotificationArea.hide()
    setDownloadStatus(true)
    myCurrentlyInstalling.add(packageName)
    myPackageListener?.operationStarted(packageName)
    myPackages.repaint()
  }

  private fun handleInstallationFinished(packageName: String, errorDescription: PackageManagementService.ErrorDescription?) {
    myPackageListener?.operationFinished(packageName, errorDescription)
    setDownloadStatus(false)
    myNotificationArea.showResult(packageName, errorDescription)

    updateInstalledPackages()

    myCurrentlyInstalling.remove(packageName)
    myPackages.repaint()
  }

  private fun updateInstalledPackages() {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val installedPackages: List<String> = myController.installedPackagesList.map { it.name }
        UIUtil.invokeLaterIfNeeded(Runnable {
          myInstalledPackages.clear()
          myInstalledPackages.addAll(installedPackages)
        })
      }
      catch (e: ExecutionException) {
        LOG.info("Error updating list of installed packages", e)
      }
    }
  }

  fun initModel() {
    setDownloadStatus(true)
    val application = ApplicationManager.getApplication()
    application.executeOnPooledThread {
      try {
        myPackagesModel = PackagesModel(myController.getAllPackages())

        application.invokeLater(Runnable {
          myPackages.setModel(myPackagesModel!!)
          myFilter.filter()
          doSelectPackage(mySelectedPackageName)
          setDownloadStatus(false)
        }, ModalityState.any())
      }
      catch (e: IOException) {
        application.invokeLater(Runnable {
          if (myMainPanel.isShowing()) {
            Messages.showErrorDialog(myMainPanel,
                                     IdeBundle.message("error.loading.package.list", e.message),
                                     IdeBundle.message("packages.title"))
          }
          LOG.info("Error initializing model", e)
          setDownloadStatus(false)
        }, ModalityState.any())
      }
    }
  }

  private fun doSelectPackage(packageName: String?) {
    val packagesModel = myPackages.model as? PackagesModel
    if (packageName == null || packagesModel == null) {
      return
    }
    for (i in 0 until packagesModel.size) {
      val repoPackage = packagesModel.getElementAt(i)
      if ((packageName == repoPackage.name)) {
        myPackages.selectedIndex = i
        myPackages.ensureIndexIsVisible(i)
        break
      }
    }
  }

  protected fun setDownloadStatus(status: Boolean) {
    myPackages.setPaintBusy(status)
    myPackages.emptyText.text = if (status)
      LangBundle.message("packages.list.downloading")
    else
      LangBundle.message("status.text.nothing.to.show")
  }

  override fun createCenterPanel() = myMainPanel

  fun setOptionsText(optionsText: String) {
    myOptionsField.text = optionsText
  }

  private inner class MyPackageFilter : FilterComponent("PACKAGE_FILTER", 5) {
    init {
      textEditor.addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          if (e.keyCode == KeyEvent.VK_ENTER) {
            e.consume()
            filter()
            IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
              IdeFocusManager.getGlobalInstance().requestFocus(myPackages, true)
            }
          }
          else if (e.keyCode == KeyEvent.VK_ESCAPE) {
            onEscape(e)
          }
        }
      })
    }

    override fun filter() {
      myPackagesModel?.filter(filter)
    }
  }

  private inner class PackagesModel(packages: MutableList<RepoPackage>) : CollectionListModel<RepoPackage?>(packages) {
    private val myFilteredOut: MutableList<RepoPackage> = ArrayList()
    private var myView: MutableList<RepoPackage> = ArrayList()

    init {
      myView = packages
    }

    fun filter(filter: String) {
      val toProcess: MutableCollection<RepoPackage> = toProcess()

      toProcess.addAll(myFilteredOut)
      myFilteredOut.clear()

      val filtered = ArrayList<RepoPackage>()

      var toSelect: RepoPackage? = null
      for (repoPackage: RepoPackage in toProcess) {
        val packageName = repoPackage.name
        if (StringUtil.containsIgnoreCase(packageName, filter)) {
          filtered.add(repoPackage)
        }
        else {
          myFilteredOut.add(repoPackage)
        }
        if (StringUtil.equalsIgnoreCase(packageName, filter)) toSelect = repoPackage
      }
      filter(filtered, toSelect)
    }

    fun filter(filtered: List<RepoPackage>, toSelect: RepoPackage?) {
      myView.clear()
      myPackages.clearSelection()
      myView.addAll((filtered))
      if (toSelect != null) myPackages.setSelectedValue(toSelect, true)
      myView.sort()
      fireContentsChanged(this, 0, myView.size)
    }

    override fun getElementAt(index: Int): RepoPackage {
      return myView[index]
    }

    private fun toProcess(): ArrayList<RepoPackage> {
      return ArrayList(myView)
    }

    override fun getSize(): Int {
      return myView.size
    }
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return (myFilter as FilterComponent?)?.textEditor
  }

  private inner class MyPackageSelectionListener : ListSelectionListener {
    override fun valueChanged(event: ListSelectionEvent) {
      myOptionsCheckBox.isEnabled = myPackages.selectedIndex >= 0
      myVersionCheckBox.isEnabled = myPackages.selectedIndex >= 0
      myOptionsCheckBox.isSelected = false
      myVersionCheckBox.isSelected = false
      myVersionComboBox.isEnabled = false
      myOptionsField.isEnabled = false
      myDescriptionTextArea.text = IdeBundle.message("loading.in.progress")
      val pyPackage = myPackages.selectedValue
      if (pyPackage is RepoPackage) {
        val packageName = pyPackage.name
        mySelectedPackageName = packageName
        myVersionComboBox.removeAllItems()
        if (myVersionCheckBox.isEnabled) {
          myController.fetchPackageVersions(packageName, object : CatchingConsumer<List<String>?, Exception?> {
            override fun consume(releases: List<String>?) {
              releases ?: return
              ApplicationManager.getApplication().invokeLater({
                                                                if (myPackages.getSelectedValue() === pyPackage) {
                                                                  myVersionComboBox.removeAllItems()
                                                                  for (release in releases) {
                                                                    myVersionComboBox.addItem(release)
                                                                  }
                                                                }
                                                              }, ModalityState.any())
            }

            override fun consume(e: Exception?) {
              LOG.info("Error retrieving releases", e)
            }
          })
        }
        myInstallButton.isEnabled = !myCurrentlyInstalling.contains(packageName)

        myController.fetchPackageDetails(packageName, object : CatchingConsumer<String?, Exception?> {
          override fun consume(details: @Nls String?) {
            UIUtil.invokeLaterIfNeeded {
              if (myPackages.getSelectedValue() === pyPackage) {

                @Suppress("HardCodedStringLiteral")
                myDescriptionTextArea.text = if (details.isNullOrBlank() || details.contains(
                    "<body style=\"font-family: Arial,serif; font-size: 12pt; margin: 5px 5px;\"></body>"))
                  IdeBundle.message("no.information.available")
                else details
                myDescriptionTextArea.setCaretPosition(0)
              } /* else {
                 do nothing, because other package gets selected
              }*/
            }
          }

          override fun consume(exception: Exception?) {
            UIUtil.invokeLaterIfNeeded { myDescriptionTextArea.text = IdeBundle.message("no.information.available") }
            LOG.info("Error retrieving package details", exception)
          }
        })
      }
      else {
        myInstallButton.isEnabled = false
        myDescriptionTextArea.text = ""
      }
    }
  }

  override fun createActions(): Array<Action> {
    return emptyArray()
  }

  inner class MyTableRenderer : ListCellRenderer<RepoPackage> {
    private val myNameComponent = SimpleColoredComponent()
    private val myRepositoryComponent = SimpleColoredComponent()
    private val myPanel = JPanel(BorderLayout())

    init {
      myPanel.add(myNameComponent, BorderLayout.WEST)
      myPanel.add(myRepositoryComponent, BorderLayout.EAST)
    }

    override fun getListCellRendererComponent(list: JList<out RepoPackage>,
                                              repoPackage: RepoPackage,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      myNameComponent.clear()
      myRepositoryComponent.clear()

      val packageName = repoPackage.name
      val blueText = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, PlatformColors.BLUE)
      val defaultForeground = if (isSelected) list.selectionForeground else list.foreground
      val defaultText = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, defaultForeground)
      myNameComponent.append(packageName, if (myInstalledPackages.contains(packageName)) blueText else defaultText, true)
      if (myCurrentlyInstalling.contains(packageName)) {
        myNameComponent.append(LangBundle.message("package.component.installing.suffix"), blueText, false)
      }
      val repoUrl = repoPackage.repoUrl
      if (repoUrl?.isNotBlank() == true) {
        myRepositoryComponent.append((repoUrl), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES, false)
      }

      if (isSelected) {
        myPanel.background = list.selectionBackground
      }
      else {
        myPanel.background = if (index % 2 == 1) UIUtil.getListBackground() else UIUtil.getDecoratedRowColor()
      }

      return myPanel
    }
  }

  companion object {
    /** Number of characters used to calculate the fixed width of package entry in the list.*/
    private const val CHARACTERS_PER_CELL = 10
    private val LOG = thisLogger()
  }
}