// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.projectTypeStep

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.projectWizard.NewProjectWizardCollector.logSearchChanged
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.newProjectWizard.TemplatesGroup
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.observable.util.whenTextChanged
import com.intellij.openapi.observable.util.whenTextChangedFromUi
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.ui.*
import com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
import com.intellij.ui.SingleSelectionModel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Graphics
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.*

@ApiStatus.Internal
internal class ProjectTypeList(context: WizardContext) {

  private val searchTextField: SearchTextField
  private val list: JBList<TemplateGroupItem>
  private val model: ProjectTypeListModel

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

  init {
    model = ProjectTypeListModel()

    list = JBList(model)
    list.setSelectionModel(SingleSelectionModel())
    list.setCellRenderer(ProjectTypeListRenderer(context, model))

    searchTextField = SearchTextField(false)
    searchTextField.textEditor.border = JBUI.Borders.empty(2, 5, 2, 0)
    searchTextField.whenTextChangedFromUi {
      logSearchChanged(context, searchTextField.text.length, list.model.size)
    }

    list.emptyText.setText(IdeBundle.message("plugins.configurable.nothing.found"))
    list.emptyText.appendSecondaryText(IdeBundle.message("plugins.configurable.search.in.marketplace"), LINK_PLAIN_ATTRIBUTES) {
      ShowSettingsUtil.getInstance().showSettingsDialog(context.project, PluginManagerConfigurable::class.java) { configurable ->
        configurable.openMarketplaceTab(searchTextField.text)
      }
    }

    val scrollPane = JBScrollPane(list)
    scrollPane.border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)

    component = JPanel(BorderLayout())
    component.border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0)
    component.add(searchTextField, BorderLayout.NORTH)
    component.add(scrollPane, BorderLayout.CENTER)
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

    fun setLanguageGeneratorItems(items: List<LanguageGeneratorItem>) {
      languageGeneratorItems.clear()
      languageGeneratorItems.addAll(items)
      fireContentsChanged(this, 0, languageGeneratorItems.size - 1)
    }

    fun setTemplateGroupItems(items: List<TemplateGroupItem>) {
      templateGroupItems.clear()
      templateGroupItems.addAll(items)
      val prefixSize = languageGeneratorItems.size
      fireContentsChanged(this, prefixSize, prefixSize + templateGroupItems.size - 1)
    }

    fun setUserTemplateGroupItems(items: List<UserTemplateGroupItem>) {
      userTemplateGroupItems.clear()
      userTemplateGroupItems.addAll(items)
      val prefixSize = languageGeneratorItems.size + templateGroupItems.size
      fireContentsChanged(this, prefixSize, prefixSize + userTemplateGroupItems.size - 1)
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
      mySeparatorComponent.font = JBUI.Fonts.smallFont()

      if (value.group.isPromo) {
        myNextStepLabel.icon = AllIcons.Ultimate.Lock
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

    override fun getTooltipFor(value: TemplateGroupItem): String? {
      return value.group.description
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

  companion object {

    private const val PROJECT_WIZARD_GROUP = "project.wizard.group"
    private val LOG = Logger.getInstance("com.intellij.ide.projectWizard.ProjectTypeStep")

    @TestOnly
    @JvmStatic
    fun resetStoredSelectionForTests() {
      PropertiesComponent.getInstance().setValue(PROJECT_WIZARD_GROUP, null)
    }
  }
}