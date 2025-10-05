// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.projectTypeStep

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.projectWizard.NewProjectWizardCollector.logInstallPluginDialogShowed
import com.intellij.ide.projectWizard.NewProjectWizardCollector.logInstallPluginPopupShowed
import com.intellij.ide.projectWizard.NewProjectWizardCollector.logSearchChanged
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.newProjectWizard.TemplatesGroup
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.observable.util.whenTextChanged
import com.intellij.openapi.observable.util.whenTextChangedFromUi
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListItemDescriptor
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.getInstallAndEnableTask
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
import com.intellij.ui.SingleSelectionModel
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Graphics
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.*

internal class ProjectTypeList(
  private val context: WizardContext
) {
  private val searchTextField: SearchTextField
  private val list: JBList<TemplateGroupItem>
  private val model: ProjectTypeListModel
  private val languagePluginFooterLink: JComponent

  val component: JComponent

  fun getSelectedTemplateGroup(): TemplatesGroup? {
    return list.getSelectedValue()?.group
  }

  fun setSelectedTemplateGroup(groupName: String) {
    val groupItem = model.items.find { it.group.name == groupName } ?: return
    list.setSelectedValue(groupItem, true)
  }

  fun setSelectedTemplateGroup(group: TemplatesGroup) {
    setSelectedTemplateGroup(group.name)
  }

  @TestOnly
  fun getAvailableTemplateGroups(): String {
    return model.items.joinToString { it.group.name }
  }

  fun setLanguageGeneratorItems(items: List<LanguageGeneratorItem>) {
    LOG.debug("Language generator items: $items")
    model.setLanguageGeneratorItems(items)
  }

  fun addLanguageGeneratorItem(item: LanguageGeneratorItem) {
    LOG.debug("Language generator added: $item")
    val index = model.addLanguageGeneratorItem(item)
    list.selectedIndex = index
  }

  fun removeLanguageGeneratorItem(languageName: String) {
    LOG.debug("Language generator removed: $languageName")
    model.removeLanguageGeneratorItem(languageName)
    list.selectedIndex = 0
  }

  fun setTemplateGroupItems(items: List<TemplateGroupItem>) {
    LOG.debug("Template group items: $items")
    model.setTemplateGroupItems(items)
  }

  fun setUserTemplateGroupItems(items: List<UserTemplateGroupItem>) {
    LOG.debug("User template items: $items")
    model.setUserTemplateGroupItems(items)
  }

  fun whenProjectTemplateGroupSelected(action: Consumer<TemplatesGroup>) {
    list.addListSelectionListener { event ->
      if (!event.valueIsAdjusting) {
        val group = getSelectedTemplateGroup()
        if (group != null) {
          action.accept(group)
        }
      }
    }
  }

  fun restoreSelection() {
    whenProjectTemplateGroupSelected { group ->
      PropertiesComponent.getInstance().setValue(PROJECT_WIZARD_GROUP, group.id)
      LOG.debug("Stored selection groupId=${group.id}", Throwable())
    }
    val groupId = PropertiesComponent.getInstance().getValue(PROJECT_WIZARD_GROUP)
    LOG.debug("Restored selection groupId=$groupId")

    val groupItem = model.items.find { it.group.id == groupId }
    if (groupItem != null) {
      list.setSelectedValue(groupItem, true)
    }
    else {
      list.selectedIndex = 0
    }
  }

  fun installFilteringListModel(namer: Function<TemplateGroupItem, String>, showEmptyStatus: Runnable) {
    val speedSearch = SpeedSearch()
    val filteringListModel = NameFilteringListModel(list.model, namer::apply, speedSearch::shouldBeShowing, speedSearch::getFilter)

    val modelSize = list.model.size
    val selectedIndex = list.selectedIndex
    list.setModel(filteringListModel)
    if (list.model.size == modelSize) {
      list.selectedIndex = selectedIndex
    }

    searchTextField.whenTextChanged {
      speedSearch.updatePattern(searchTextField.text)
      filteringListModel.refilter()
      list.selectedIndex = 0
      if (filteringListModel.size == 0) {
        showEmptyStatus.run()
      }
    }
  }

  private fun showInstallPluginDialog(plugin: WizardPlugin) {
    logInstallPluginDialogShowed(context, plugin.name)
    ProgressManager.getInstance().run(
      getInstallAndEnableTask(null, setOf(PluginId.getId(plugin.id)), true, true, null) { }
    )
  }

  private fun showInstallPluginDialog() {
    logInstallPluginDialogShowed(context)
    showInstallPluginDialog {
      openMarketplaceTab(searchTextField.text)
    }
  }

  private fun showInstallPluginPopup() {
    logInstallPluginPopupShowed(context)
    val installedLanguagePlugins = PluginManager.getPlugins().map { it.pluginId.idString }.toSet()
    val additionalLanguagePlugins = getAdditionalWizardPlugins().filter { it.id !in installedLanguagePlugins }
    if (additionalLanguagePlugins.isEmpty()) {
      showInstallPluginDialog()
    }
    else {
      showInstallPluginPopup(additionalLanguagePlugins)
    }
  }

  private fun showInstallPluginDialog(configure: PluginManagerConfigurable.() -> Unit) {
    val configurable = PluginManagerConfigurable()
    AutoCloseable(configurable::disposeUIResources).use {
      configurable.configure()
      val dialogBuilder = DialogBuilder(component)
      dialogBuilder.title(UIBundle.message("newProjectWizard.ProjectTypeStep.InstallPluginAction.title"))
      dialogBuilder.centerPanel(
        JBUI.Panels.simplePanel(configurable.createComponent()!!.apply {
          border = JBUI.Borders.customLine(JBColor.border(), 0, 1, 1, 1)
        }).addToTop(configurable.topComponent.apply {
          preferredSize = JBDimension(preferredSize.width, 40)
        })
      )
      dialogBuilder.addOkAction()
      dialogBuilder.addCancelAction()
      if (dialogBuilder.showAndGet() && configurable.isModified) {
        configurable.apply()
      }
    }
  }

  private fun showInstallPluginPopup(additionalWizardPlugins: List<WizardPlugin>) {
    val link = ActionLink(UIBundle.message("newProjectWizard.ProjectTypeStep.InstallPluginAction.advertiser")) { showInstallPluginDialog() }
    link.toolTipText = null

    val languagePluginAdvertiserLink = JBPanel<JBPanel<*>>(BorderLayout())
    languagePluginAdvertiserLink.border = JBUI.CurrentTheme.Advertiser.border()
    languagePluginAdvertiserLink.foreground = JBUI.CurrentTheme.Advertiser.foreground()
    languagePluginAdvertiserLink.background = JBUI.CurrentTheme.Advertiser.background()
    languagePluginAdvertiserLink.withMinimumWidth(JBUI.scale(220))
    languagePluginAdvertiserLink.withPreferredWidth(JBUI.scale(220))
    languagePluginAdvertiserLink.add(link, BorderLayout.WEST)

    return JBPopupFactory.getInstance()
      .createPopupChooserBuilder(additionalWizardPlugins)
      .setRenderer(LanguagePluginRenderer())
      .setTitle(UIBundle.message("newProjectWizard.ProjectTypeStep.InstallPluginAction.title"))
      .setAutoselectOnMouseMove(true)
      .setNamerForFiltering { it.name }
      .setMovable(true)
      .setAdvertiser(languagePluginAdvertiserLink)
      .setResizable(false)
      .setRequestFocus(true)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setItemChosenCallback(::showInstallPluginDialog)
      .createPopup()
      .show(RelativePoint.getSouthWestOf(languagePluginFooterLink))
  }

  init {
    model = ProjectTypeListModel()

    list = JBList(model)
    list.setSelectionModel(SingleSelectionModel())
    list.setCellRenderer(ProjectTypeListRenderer(context, model))
    list.accessibleContext.accessibleName = UIBundle.message("newProjectWizard.ProjectTypeStep.list.accessible.name")

    searchTextField = SearchTextField(false)
    searchTextField.textEditor.border = JBUI.Borders.empty(2, 5, 2, 0)
    searchTextField.whenTextChangedFromUi {
      logSearchChanged(context, searchTextField.text.length, list.model.size)
    }

    list.emptyText.setText(IdeBundle.message("plugins.configurable.nothing.found"))
    list.emptyText.appendSecondaryText(IdeBundle.message("plugins.configurable.search.in.marketplace"), LINK_PLAIN_ATTRIBUTES) {
      showInstallPluginDialog()
    }

    val scrollPane = JBScrollPane(list)
    scrollPane.border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0)

    val link = ActionLink(UIBundle.message("newProjectWizard.ProjectTypeStep.InstallPluginAction.name")) { showInstallPluginPopup() }
    link.toolTipText = UIBundle.message("newProjectWizard.ProjectTypeStep.InstallPluginAction.description")

    languagePluginFooterLink = JBPanel<JBPanel<*>>(BorderLayout())
    languagePluginFooterLink.border = JBUI.CurrentTheme.Advertiser.border()
    languagePluginFooterLink.withMinimumHeight(JBUI.scale(40))
    languagePluginFooterLink.withPreferredHeight(JBUI.scale(40))
    languagePluginFooterLink.add(link, BorderLayout.WEST)

    component = JPanel(BorderLayout())
    component.border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0)
    component.add(searchTextField, BorderLayout.NORTH)
    component.add(scrollPane, BorderLayout.CENTER)
    component.add(languagePluginFooterLink, BorderLayout.SOUTH)
  }

  private class ProjectTypeListModel : AbstractListModel<TemplateGroupItem>() {

    private val languageGeneratorItems = ArrayList<LanguageGeneratorItem>()
    private val templateGroupItems = ArrayList<TemplateGroupItem>()
    private val userTemplateGroupItems = ArrayList<UserTemplateGroupItem>()

    val items: List<TemplateGroupItem>
      get() = languageGeneratorItems + templateGroupItems + userTemplateGroupItems

    override fun getSize(): Int {
      return items.size
    }

    override fun getElementAt(index: Int): TemplateGroupItem {
      return items[index]
    }

    fun getLanguageGeneratorItems(): List<LanguageGeneratorItem> {
      return languageGeneratorItems
    }

    fun setLanguageGeneratorItems(items: List<LanguageGeneratorItem>) {
      setItems(languageGeneratorItems, 0, items)
    }

    fun addLanguageGeneratorItem(item: LanguageGeneratorItem): Int {
      return addItem(languageGeneratorItems, 0, item)
    }

    fun removeLanguageGeneratorItem(languageName: String) {
      removeItem(languageGeneratorItems, 0, languageName)
    }

    fun setTemplateGroupItems(items: List<TemplateGroupItem>) {
      setItems(templateGroupItems, languageGeneratorItems.size, items)
    }

    fun setUserTemplateGroupItems(items: List<UserTemplateGroupItem>) {
      setItems(userTemplateGroupItems, languageGeneratorItems.size + templateGroupItems.size, items)
    }

    private fun <T : TemplateGroupItem> setItems(collection: MutableList<T>, offset: Int, items: List<T>) {
      collection.clear()
      collection.addAll(items)
      collection.sortBy { it.group }
      fireContentsChanged(this, offset, offset + collection.size - 1)
    }

    private fun <T : TemplateGroupItem> addItem(collection: MutableList<T>, offset: Int, item: T): Int {
      var index = collection.indexOfFirst { it.group >= item.group }
      if (index < 0) index = 0
      collection.add(index, item)
      fireIntervalAdded(this, offset + index, offset + index)
      return offset + index
    }

    private fun <T : TemplateGroupItem> removeItem(collection: MutableList<T>, offset: Int, name: String) {
      val index = collection.indexOfFirst { name == it.group.name }
      if (index >= 0) {
        collection.removeAt(index)
        fireIntervalRemoved(this, offset + index, offset + index)
      }
    }
  }

  private class ProjectTypeListRenderer(
    context: WizardContext,
    model: ProjectTypeListModel
  ) : GroupedItemsListRenderer<TemplateGroupItem>(
    ProjectTypeListItemDescriptor(context, model)
  ) {

    override fun customizeComponent(
      list: JList<out TemplateGroupItem>,
      value: TemplateGroupItem,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean
    ) {
      mySeparatorComponent.border = when (index == 0) {
        true -> JBUI.Borders.empty(5, 8, 5, 0)
        else -> JBUI.Borders.empty(20, 8, 5, 0)
      }
      mySeparatorComponent.setCaptionCentered(false)
      mySeparatorComponent.font = JBFont.label().deriveFont(JBUI.scale(12))

      myNextStepLabel.icon = when (value.group.isPromo) {
        true -> AllIcons.Ultimate.Lock
        else -> null
      }

      myTextLabel.border = JBUI.Borders.empty(5, 0)
    }

    @Suppress("DEPRECATION")
    override fun createSeparator(): SeparatorWithText {
      return object : SeparatorWithText() {
        override fun paintLinePart(g: Graphics, xMin: Int, xMax: Int, hGap: Int, y: Int) {}
      }
    }
  }

  private class ProjectTypeListItemDescriptor(
    private val context: WizardContext,
    private val model: ProjectTypeListModel
  ) : ListItemDescriptorAdapter<TemplateGroupItem>() {

    override fun getTextFor(value: TemplateGroupItem): String? {
      return value.group.name
    }

    override fun getIconFor(value: TemplateGroupItem): Icon {
      return value.group.icon ?: EmptyIcon.ICON_16
    }

    override fun getCaptionAboveOf(value: TemplateGroupItem): String {
      return when (value) {
        is LanguageGeneratorItem ->
          UIBundle.message("list.caption.group.newProject", context.isCreatingNewProjectInt())
        is UserTemplateGroupItem ->
          UIBundle.message("list.caption.group.templates")
        else ->
          UIBundle.message("list.caption.group.generators")
      }
    }

    override fun hasSeparatorAboveOf(value: TemplateGroupItem): Boolean {
      val index = model.items.indexOf(value)
      if (index < 0) return false
      if (index == 0) return true
      val upperItem = model.items[index - 1]
      if (value !is LanguageGeneratorItem && upperItem is LanguageGeneratorItem) {
        return true
      }
      if (value is UserTemplateGroupItem && upperItem !is UserTemplateGroupItem) {
        return true
      }
      return false
    }
  }

  private class LanguagePluginRenderer : GroupedItemsListRenderer<WizardPlugin>(LanguagePluginRendererDescriptor()) {

    override fun customizeComponent(
      list: JList<out WizardPlugin>,
      value: WizardPlugin,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean
    ) {
      myTextLabel.border = JBUI.Borders.empty(5, 0)
    }
  }

  private class LanguagePluginRendererDescriptor : ListItemDescriptor<WizardPlugin> {
    override fun getTextFor(value: WizardPlugin): String = value.name
    override fun getTooltipFor(value: WizardPlugin): String? = null
    override fun getIconFor(value: WizardPlugin): Icon = value.icon
    override fun hasSeparatorAboveOf(value: WizardPlugin): Boolean = false
    override fun getCaptionAboveOf(value: WizardPlugin): String? = null
  }
}

private class WizardPlugin(
  val id: String,
  val name: @NlsSafe String,
  val icon: Icon
)

private const val PROJECT_WIZARD_GROUP = "project.wizard.group"
private val LOG = Logger.getInstance("com.intellij.ide.projectWizard.ProjectTypeStep")

private fun getAdditionalWizardPlugins(): List<WizardPlugin> = buildList {
  if (PlatformUtils.isIdeaCommunity()) {
    add(WizardPlugin("PythonCore", Language.PYTHON, AllIcons.Language.Python))
    add(WizardPlugin("DevKit", "Plugin DevKit", AllIcons.Nodes.Plugin))
    add(WizardPlugin("org.intellij.scala", Language.SCALA, AllIcons.Language.Scala))
  }
  else {
    add(WizardPlugin("org.jetbrains.plugins.go", Language.GO, AllIcons.Language.GO))
    add(WizardPlugin("com.jetbrains.php", Language.PHP, AllIcons.Language.Php))
    add(WizardPlugin("Pythonid", Language.PYTHON, AllIcons.Language.Python))
    add(WizardPlugin("DevKit", "Plugin DevKit", AllIcons.Nodes.Plugin))
    add(WizardPlugin("org.jetbrains.plugins.ruby", Language.RUBY, AllIcons.Language.Ruby))
    add(WizardPlugin("com.jetbrains.rust", Language.RUST, AllIcons.Language.Rust))
    add(WizardPlugin("org.intellij.scala", Language.SCALA, AllIcons.Language.Scala))
  }
}

@TestOnly
fun resetStoredSelectionForTests() {
  PropertiesComponent.getInstance().setValue(PROJECT_WIZARD_GROUP, null)
}