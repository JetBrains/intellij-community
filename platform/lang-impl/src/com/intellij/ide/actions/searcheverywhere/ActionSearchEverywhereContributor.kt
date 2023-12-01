// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.GotoActionAction
import com.intellij.ide.actions.SetShortcutAction
import com.intellij.ide.actions.searcheverywhere.footer.ActionHistoryManager
import com.intellij.ide.actions.searcheverywhere.footer.createActionExtendedInfo
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.BooleanOptionDescription.RequiresRebuild
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.ActionAsyncProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.GotoActionListCellRenderer
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.Utils.runUpdateSessionForActionSearch
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Processor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.lang.ref.WeakReference
import java.util.*
import javax.swing.ListCellRenderer

private val LOG = logger<ActionSearchEverywhereContributor>()

open class ActionSearchEverywhereContributor : WeightedSearchEverywhereContributor<MatchedValue>, LightEditCompatible, SearchEverywhereExtendedInfoProvider {
  private val myProject: Project?
  private val myContextComponent: WeakReference<Component?>
  protected val model: GotoActionModel
  private val myProvider: ActionAsyncProvider
  protected var myDisabledActions: Boolean = false

  private val isRecentEnabled: Boolean
    get() = Registry.`is`("search.everywhere.recents") || ApplicationManager.getApplication().isInternal

  constructor(other: ActionSearchEverywhereContributor) {
    myProject = other.myProject
    myContextComponent = other.myContextComponent
    model = other.model
    myProvider = other.myProvider
    myDisabledActions = other.myDisabledActions
  }

  constructor(project: Project?, contextComponent: Component?, editor: Editor?) {
    myProject = project
    myContextComponent = WeakReference(contextComponent)
    model = GotoActionModel(project, contextComponent, editor)
    myProvider = ActionAsyncProvider(model)
  }

  override fun getGroupName(): String = IdeBundle.message("search.everywhere.group.name.actions")

  override fun getAdvertisement(): String? {
    if (SearchEverywhereUI.isExtendedInfoEnabled()) return null

    val altEnterShortcutSet = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)
    val altEnter: @NlsSafe String = KeymapUtil.getFirstKeyboardShortcutText(altEnterShortcutSet)
    return IdeBundle.message("press.0.to.assign.a.shortcut", altEnter)
  }

  override fun createExtendedInfo(): @Nls ExtendedInfo? = createActionExtendedInfo(myProject)


  fun includeNonProjectItemsText(): @NlsContexts.Checkbox String = IdeBundle.message("checkbox.disabled.included")

  override fun getSortWeight(): Int = 400

  override fun isShownInSeparateTab(): Boolean = true

  override fun fetchWeightedElements(pattern: String,
                                     progressIndicator: ProgressIndicator,
                                     consumer: Processor<in FoundItemDescriptor<MatchedValue>>) {
    ProgressManager.getInstance().runProcess({
      runBlockingCancellable {
        model.buildGroupMappings()
        runUpdateSessionForActionSearch(model.getUpdateSession()) { presentationProvider ->
          doFetchItems(this, presentationProvider, pattern) { consumer.process(it) }
        }
      }
    }, progressIndicator)
  }

  override fun getActions(onChanged: Runnable): List<AnAction> {
    return listOf<AnAction>(object : CheckBoxSearchEverywhereToggleAction(includeNonProjectItemsText()) {
      override fun isEverywhere(): Boolean {
        return myDisabledActions
      }

      override fun setEverywhere(state: Boolean) {
        myDisabledActions = state
        onChanged.run()
      }
    })
  }

  override fun getElementsRenderer(): ListCellRenderer<in MatchedValue> {
    return GotoActionListCellRenderer(
      { description: OptionDescription? ->
        model.getGroupName(
          description!!)
      }, true)
  }

  override fun showInFindResults(): Boolean = false

  override fun getSearchProviderId(): String = ActionSearchEverywhereContributor::class.java.simpleName

  override fun getDataForItem(element: MatchedValue, dataId: String): Any? {
    return if (SetShortcutAction.SELECTED_ACTION.`is`(dataId)) getAction(element) else null
  }

  override fun getItemDescription(element: MatchedValue): String? {
    val action = getAction(element)
    if (action == null) {
      return null
    }
    val description = action.templatePresentation.description
    if (UISettings.getInstance().showInplaceCommentsInternal) {
      val presentableId = StringUtil.notNullize(ActionManager.getInstance().getId(action), "class: " + action.javaClass.name)
      return "[$presentableId] ${description ?: ""}"
    }
    return description
  }

  override fun processSelectedItem(item: MatchedValue, modifiers: Int, text: String): Boolean {
    if (modifiers == InputEvent.ALT_MASK) {
      showAssignShortcutDialog(myProject, item)
      return true
    }

    val selected = item.value

    if (selected is BooleanOptionDescription) {
      if (selected is RequiresRebuild) {
        model.clearCaches() // release references to plugin actions so that the plugin can be unloaded successfully
        myProvider.clearIntentions()
      }
      selected.setOptionState(!selected.isOptionEnabled)
      return false
    }

    if (isRecentEnabled) {
      saveRecentAction(item)
    }

    GotoActionAction.openOptionOrPerformAction(selected, text, myProject, myContextComponent.get(), modifiers)
    val inplaceChange = (selected is GotoActionModel.ActionWrapper && selected.action is ToggleAction)
    return !inplaceChange
  }

  class Factory : SearchEverywhereContributorFactory<MatchedValue> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<MatchedValue> {
      return ActionSearchEverywhereContributor(
        initEvent.project,
        initEvent.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT),
        initEvent.getData(CommonDataKeys.EDITOR))
    }
  }

  override fun isEmptyPatternSupported(): Boolean {
    return isRecentEnabled
  }

  protected fun doFetchItems(scope: CoroutineScope,
                             presentationProvider: suspend (AnAction) -> Presentation,
                             pattern: String,
                             consumer: suspend (FoundItemDescriptor<MatchedValue>) -> Boolean) {

    if (pattern.isBlank()) {
      processRecentActions(scope, presentationProvider, pattern, consumer)
      return
    }

    LOG.debug("Start actions search")

    myProvider.filterElements(scope, presentationProvider, pattern) { element: MatchedValue? ->
      if (element == null) {
        LOG.error("Null action has been returned from model")
        return@filterElements true
      }
      val isActionWrapper = element.value is GotoActionModel.ActionWrapper
      if (!myDisabledActions && isActionWrapper && !(element.value as GotoActionModel.ActionWrapper).isAvailable) {
        return@filterElements true
      }

      val descriptor = FoundItemDescriptor(element, element.matchingDegree)
      consumer(descriptor)
    }
  }

  protected fun processRecentActions(scope: CoroutineScope,
                                     presentationProvider: suspend (AnAction) -> Presentation,
                                     pattern: String,
                                     consumer: suspend (FoundItemDescriptor<MatchedValue>) -> Boolean) {
    if (!isRecentEnabled) return
    if (SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID == SearchEverywhereManager.getInstance(myProject).selectedTabID) return

    val actionIDs: Set<String> = ActionHistoryManager.getInstance().state.ids
    myProvider.processActions(scope, presentationProvider, pattern, actionIDs) { element: MatchedValue ->
      if (!myDisabledActions && !(element.value as GotoActionModel.ActionWrapper).isAvailable) return@processActions true
      val action = getAction(element)
      if (action == null) return@processActions true

      val id = serviceAsync<ActionManager>().getId(action)
      val degree = actionIDs.indexOf(id)
      consumer(FoundItemDescriptor(element, degree))
    }
  }

  companion object {
    fun showAssignShortcutDialog(myProject: Project?, value: MatchedValue) {
      val action = getAction(value)
      if (action == null) return

      val id = ActionManager.getInstance().getId(action)

      val activeKeymap = Optional.ofNullable(KeymapManager.getInstance())
        .map { obj: KeymapManager -> obj.activeKeymap }
        .orElse(null)
      if (activeKeymap == null) return

      ApplicationManager.getApplication().invokeLater {
        val window = if (myProject != null
        ) WindowManager.getInstance().suggestParentWindow(myProject)
        else KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
        if (window == null) return@invokeLater
        KeymapPanel.addKeyboardShortcut(id!!, ActionShortcutRestrictions.getInstance().getForActionId(id), activeKeymap, window)
      }
    }
  }
}

private fun getAction(element: MatchedValue): AnAction? {
  var value = element.value
  if (value is GotoActionModel.ActionWrapper) {
    value = value.action
  }
  return if (value is AnAction) value else null
}

private fun saveRecentAction(selected: MatchedValue) {
  val action = getAction(selected)
  if (action == null) return

  val id = ActionManager.getInstance().getId(action)
  if (id == null) return

  val ids: MutableSet<String> = ActionHistoryManager.getInstance().state.ids
  if (ids.size < Registry.intValue("search.everywhere.recents.limit")) {
    ids.add(id)
  }
}
