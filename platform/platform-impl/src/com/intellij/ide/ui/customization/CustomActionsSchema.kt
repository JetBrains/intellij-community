// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.ui.customization

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.customization.CustomizableActionGroupProvider.CustomizableActionGroupRegistrar
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil
import com.intellij.openapi.keymap.impl.ui.Group
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IconLoader.getDisabledIcon
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.serviceContainer.NonInjectable
import com.intellij.ui.ExperimentalUI
import com.intellij.util.IconUtil
import com.intellij.util.SmartList
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.ui.EmptyIcon
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.FileNotFoundException
import java.net.URL
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import javax.swing.tree.DefaultMutableTreeNode

private val LOG = logger<CustomActionsSchema>()

private const val ACTIONS_SCHEMA = "custom_actions_schema"
private const val ACTIVE = "active"
private const val ELEMENT_ACTION = "action"
private const val ATTRIBUTE_ID = "id"
private const val ATTRIBUTE_ICON = "icon"
private const val GROUP = "group"
private val additionalIdToName = ConcurrentHashMap<String, String>()

private val EP_NAME = ExtensionPointName<CustomizableActionGroupProvider>("com.intellij.customizableActionGroupProvider")

@State(name = "com.intellij.ide.ui.customization.CustomActionsSchema",
       storages = [Storage(value = "customization.xml", usePathMacroManager = false)],
       category = SettingsCategory.UI)
class CustomActionsSchema(private val coroutineScope: CoroutineScope?) : PersistentStateComponent<Element?> {
  /**
   * Contain action id binding to some icon reference. It can be one of the following:
   *  * id of the other action that uses some icon
   *  * path to the SVG or PNG file of the icon
   *  * URL of the SVG or PNG icon
   */
  private val iconCustomizations = HashMap<String, String?>()
  private val lock = Any()

  @Volatile
  private var idToName: PersistentMap<String, String>

  @Volatile
  private var idToActionGroup = persistentHashMapOf<String, ActionGroup>()
  private val extGroupIds = HashSet<String>()
  private val actions = ArrayList<ActionUrl>()
  private var isFirstLoadState = true
  var modificationStamp: Int = 0
    private set

  @NonInjectable
  constructor() : this(null)

  init {
    val idToName = LinkedHashMap<String, String>()
    idToName.put(IdeActions.GROUP_MAIN_MENU, ActionsTreeUtil.getMainMenuTitle())
    val mainToolbarID = if (ExperimentalUI.isNewUI()) IdeActions.GROUP_MAIN_TOOLBAR_NEW_UI else IdeActions.GROUP_MAIN_TOOLBAR
    idToName.put(mainToolbarID, ActionsTreeUtil.getMainToolbar())
    idToName.put(IdeActions.GROUP_EDITOR_POPUP, ActionsTreeUtil.getEditorPopup())
    idToName.put(IdeActions.GROUP_EDITOR_GUTTER, ActionsTreeUtil.getEditorGutterPopupMenu())
    idToName.put(IdeActions.GROUP_EDITOR_TAB_POPUP, ActionsTreeUtil.getEditorTabPopup())
    idToName.put(IdeActions.GROUP_PROJECT_VIEW_POPUP, ActionsTreeUtil.getProjectViewPopup())
    idToName.put(IdeActions.GROUP_SCOPE_VIEW_POPUP, ActionsTreeUtil.getScopeViewPopupMenu())
    idToName.put(IdeActions.GROUP_NAVBAR_POPUP, ActionsTreeUtil.getNavigationBarPopupMenu())
    idToName.put(IdeActions.GROUP_NAVBAR_TOOLBAR, ActionsTreeUtil.getNavigationBarToolbar())
    fillExtGroups(idToName, extGroupIds)
    EP_NAME.addChangeListener({ fillExtGroups(idToName, extGroupIds) }, null)
    idToName.putAll(additionalIdToName)
    this.idToName = idToName.toPersistentMap()
  }

  companion object {
    /**
     * The original icon should be saved in template presentation when one customizes action icon
     */
    @JvmField
    val PROP_ORIGINAL_ICON: Key<Icon?> = Key.create("originalIcon")

    @JvmStatic
    fun addSettingsGroup(itemId: String, itemName: @Nls String) {
      additionalIdToName.put(itemId, itemName)

      // Need to sync new items with global instance (if it has been created)
      val customActionSchema = serviceIfCreated<CustomActionsSchema>() ?: return
      customActionSchema.idToName = customActionSchema.idToName.put(itemId, itemName)
    }

    @JvmStatic
    fun removeSettingsGroup(itemId: String) {
      additionalIdToName.remove(itemId)

      // Need to sync new items with global instance (if it has been created)
      val customActionSchema = serviceIfCreated<CustomActionsSchema>() ?: return
      customActionSchema.idToName = customActionSchema.idToName.remove(itemId)
    }

    @JvmStatic
    @RequiresBlockingContext
    fun getInstance(): CustomActionsSchema = service<CustomActionsSchema>()

    suspend fun getInstanceAsync(): CustomActionsSchema = serviceAsync<CustomActionsSchema>()

    @JvmStatic
    fun setCustomizationSchemaForCurrentProjects() {
      // increment myModificationStamp clear children cache in CustomisedActionGroup
      //  as a result do it *before* update all toolbars, menu bars and popups
      getInstance().incrementModificationStamp()
      val windowManager = WindowManagerEx.getInstanceEx()
      for (project in ProjectManager.getInstance().openProjects) {
        windowManager.getFrameHelper(project)?.updateView()
      }
      windowManager.getFrameHelper(null)?.updateView()
    }

    /**
     * @param path absolute path to the icon file, url of the icon file or url of the icon file inside jar.
     * Also, the path can contain '_dark', '@2x', '@2x_dark' suffixes, but the resulting icon will be taken
     * according to current scale and UI theme.
     */
    @ApiStatus.Internal
    @Throws(Throwable::class)
    @JvmStatic
    fun loadCustomIcon(path: String): Icon {
      val independentPath = FileUtil.toSystemIndependentName(path)
      val urlString = if (independentPath.startsWith("file:") || independentPath.startsWith("jar:")) {
        independentPath
      }
      else "file:$independentPath"

      val lastDotIndex = urlString.lastIndexOf('.')
      val (rawUrl, ext) = if (lastDotIndex != -1) {
        urlString.substring(0, lastDotIndex) to urlString.substring(lastDotIndex + 1)
      }
      else urlString to "svg"
      val possibleSuffixes = listOf("@2x_dark", "_dark@2x", "_dark", "@2x")
      val adjustedUrl = possibleSuffixes.find { rawUrl.endsWith(it) }?.let { rawUrl.removeSuffix(it) } ?: rawUrl
      val fullAdjustedUrl = "$adjustedUrl.$ext"
      return try {
        doLoadCustomIcon(fullAdjustedUrl)
      }
      catch (t: Throwable) {
        // In Light theme we do not fall back on dark icon, so if the original provided path ends with '_dark'
        // and there is no icon file without '_dark' suffix, we will fail.
        // And in this case, we just need to load the file chosen by the user.
        if (urlString != fullAdjustedUrl) {
          doLoadCustomIcon(urlString)
        }
        else throw t
      }
    }

    private fun doLoadCustomIcon(urlString: String): Icon {
      val url = URL(null, urlString)
      val icon = IconLoader.findIcon(url) ?: throw FileNotFoundException("Failed to find icon by URL: $url")
      val w = icon.iconWidth
      val h = icon.iconHeight
      if (w <= 1 || h <= 1) {
        throw FileNotFoundException("Failed to find icon by URL: $url")
      }
      if (w > EmptyIcon.ICON_18.iconWidth || h > EmptyIcon.ICON_18.iconHeight) {
        val s = EmptyIcon.ICON_18.iconWidth / w.coerceAtLeast(h).toFloat()
        // ScaledResultIcon will be returned here, so we will be unable to scale it again or get the dark version,
        // but we have nothing to do because the icon is too large
        return IconUtil.scale(icon, scale = s, ancestor = null)
      }
      return icon
    }
  }

  fun addAction(url: ActionUrl) {
    synchronized(lock) {
      if (!actions.contains(url) && !actions.remove(url.inverted)) {
        actions.add(url)
      }
    }
  }

  /**
   * Mutable list is returned.
   */
  fun getActions(): List<ActionUrl> = actions

  fun setActions(newActions: List<ActionUrl>) {
    synchronized(lock) {
      assert(actions !== newActions)
      actions.clear()
      actions.addAll(newActions)
      actions.sortWith(ActionUrlComparator)
    }
  }

  fun copyFrom(result: CustomActionsSchema) {
    synchronized(lock) {
      idToActionGroup = idToActionGroup.clear()
      actions.clear()
      val ids = java.util.List.copyOf(iconCustomizations.keys)
      iconCustomizations.clear()
      for (actionUrl in result.actions) {
        addAction(actionUrl.copy())
      }
      actions.sortWith(ActionUrlComparator)
      iconCustomizations.putAll(result.iconCustomizations)
      for (id in ids) {
        iconCustomizations.putIfAbsent(id, null)
      }
    }
  }

  fun isModified(schema: CustomActionsSchema): Boolean {
    val storedActions = schema.getActions()
    if (ApplicationManager.getApplication().isUnitTestMode && !storedActions.isEmpty()) {
      LOG.error(IdeBundle.message("custom.action.stored", storedActions))
      LOG.error(IdeBundle.message("custom.action.actual", getActions()))
    }
    if (storedActions.size != getActions().size) {
      return true
    }
    for (i in getActions().indices) {
      if (getActions()[i] != storedActions[i]) {
        return true
      }
    }
    if (schema.iconCustomizations.size != iconCustomizations.size) return true
    return iconCustomizations.keys.any { schema.getIconPath(it) != getIconPath(it) }
  }

  override fun loadState(element: Element) {
    var reload: Boolean
    synchronized(lock) {
      idToActionGroup = idToActionGroup.clear()
      actions.clear()
      iconCustomizations.clear()
      var schElement = element
      val activeName = element.getAttributeValue(ACTIVE)
      if (activeName != null) {
        for (toolbarElement in element.getChildren(ACTIONS_SCHEMA)) {
          for (o in toolbarElement.getChildren("option")) {
            if (o.getAttributeValue("name") == "myName" && o.getAttributeValue("value") == activeName) {
              schElement = toolbarElement
              break
            }
          }
        }
      }
      for (groupElement in schElement.getChildren(GROUP)) {
        val url = ActionUrl()
        url.readExternal(groupElement)
        addAction(url)
      }

      if (ApplicationManager.getApplication().isUnitTestMode) {
        @Suppress("SpellCheckingInspection")
        LOG.error(IdeBundle.message("custom.option.testmode", actions.toString()))
      }

      for (action in element.getChildren(ELEMENT_ACTION)) {
        val actionId = action.getAttributeValue(ATTRIBUTE_ID) ?: continue
        iconCustomizations.put(actionId, action.getAttributeValue(ATTRIBUTE_ICON))
      }
      reload = !isFirstLoadState
      if (isFirstLoadState) {
        isFirstLoadState = false
      }
    }
    coroutineScope?.launch {
      serviceAsync<ActionManager>()
      withContext(Dispatchers.EDT) {
        initActionIcons(updateView = reload)
      }
      if (reload) {
        withContext(Dispatchers.EDT) {
          setCustomizationSchemaForCurrentProjects()
        }
      }
    }
  }

  fun clearFirstLoadState() {
    synchronized(lock) { isFirstLoadState = false }
  }

  fun incrementModificationStamp() {
    modificationStamp++
  }

  override fun getState(): Element {
    val element = Element("state")
    for (group in actions) {
      val groupElement = Element(GROUP)
      group.writeExternal(groupElement)
      element.addContent(groupElement)
    }
    writeIcons(element)
    return element
  }

  @RequiresBlockingContext
  fun getCorrectedAction(id: String): AnAction? {
    val name = idToName.get(id) ?: return ActionManager.getInstance().getAction(id)
    return getCorrectedAction(id, name)
  }

  suspend fun getCorrectedActionAsync(id: String): ActionGroup? {
    val name = idToName.get(id) ?: return serviceAsync<ActionManager>().getAction(id) as? ActionGroup
    idToActionGroup.get(id)?.let {
      return it
    }

    val actionGroup = serviceAsync<ActionManager>().getAction(id) as? ActionGroup ?: return null
    return getOrPut(id, actionGroup, name)
  }

  fun getCorrectedAction(id: String, name: String): ActionGroup? {
    idToActionGroup.get(id)?.let {
      return it
    }

    val actionGroup = ActionManager.getInstance().getAction(id) as? ActionGroup ?: return null
    return getOrPut(id, actionGroup, name)
  }

  private fun getOrPut(id: String, actionGroup: ActionGroup, name: String): ActionGroup {
    // if a plugin is disabled
    val corrected = CustomizationUtil.correctActionGroup(/* group = */ actionGroup,
                                                         /* schema = */ this,
                                                         /* defaultGroupName = */ name,
                                                         /* rootGroupName = */ name,
                                                         /* force = */ true)
    synchronized(lock) {
      idToActionGroup.get(id)?.let {
        return it
      }
      idToActionGroup = idToActionGroup.put(id, corrected)
    }
    return corrected
  }

  fun getDisplayName(id: String): String? {
    return idToName.get(id)
  }

  fun invalidateCustomizedActionGroup(groupId: String) {
    val group = idToActionGroup.get(groupId)
    if (group is CustomisedActionGroup) {
      group.resetChildren()
    }
  }

  fun fillCorrectedActionGroups(root: DefaultMutableTreeNode) {
    val actionManager = ActionManager.getInstance()
    val path = SmartList("root")
    for ((key, value) in idToName) {
      val actionGroup = (actionManager.getAction(key) as? ActionGroup) ?: continue
      root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createCorrectedGroup(actionGroup, value, path, actions)))
    }
  }

  fun fillActionGroups(root: DefaultMutableTreeNode) {
    val actionManager = ActionManager.getInstance()
    for ((key, value) in idToName) {
      val actionGroup = (actionManager.getAction(key) as? ActionGroup) ?: continue
      //J2EE/Commander plugin was disabled
      root.add(ActionsTreeUtil.createNode(ActionsTreeUtil.createGroup(actionGroup, value, null, null, true, null, false)))
    }
  }

  fun isCorrectActionGroup(group: ActionGroup, defaultGroupName: String): Boolean {
    if (actions.isEmpty()) {
      return false
    }

    val text = group.templatePresentation.text
    if (!text.isNullOrEmpty()) {
      @Suppress("HardCodedStringLiteral")
      for (url in actions) {
        if (url.groupPath.contains(text) || url.groupPath.contains(defaultGroupName)) {
          return true
        }

        if (url.component is Group) {
          val urlGroup = url.component as Group
          if (urlGroup.children.isEmpty()) continue
          val id = if (urlGroup.name != null) urlGroup.name else urlGroup.id
          if (id == null || id == text || id == defaultGroupName) {
            return true
          }
        }
      }
      return false
    }
    return true
  }

  fun getChildActions(url: ActionUrl): List<ActionUrl> = getChildActions(url.groupPath)

  internal fun getChildActions(groupPath: List<String>): List<ActionUrl> {
    val result = ArrayList<ActionUrl>()
    for (actionUrl in actions) {
      var index = 0
      if (groupPath.size <= actionUrl.groupPath.size) {
        while (index < groupPath.size) {
          if (groupPath.get(index) != actionUrl.groupPath.get(index)) {
            break
          }
          index++
        }
        if (index == groupPath.size) {
          result.add(actionUrl)
        }
      }
    }
    return result
  }

  fun removeIconCustomization(actionId: String) {
    iconCustomizations.put(actionId, null)
  }

  fun addIconCustomization(actionId: String, iconPath: String?) {
    iconCustomizations.put(actionId, if (iconPath == null) null else FileUtil.toSystemIndependentName(iconPath))
  }

  fun getIconPath(actionId: String): String = iconCustomizations.get(actionId) ?: ""

  fun getIconCustomizations(): Map<String, String?> {
    return Collections.unmodifiableMap(iconCustomizations)
  }

  private fun writeIcons(parent: Element) {
    for (actionId in iconCustomizations.keys) {
      val icon = iconCustomizations[actionId]
      if (icon != null) {
        val action = Element(ELEMENT_ACTION)
        action.setAttribute(ATTRIBUTE_ID, actionId)
        action.setAttribute(ATTRIBUTE_ICON, icon)
        parent.addContent(action)
      }
    }
  }

  @JvmOverloads
  fun initActionIcons(updateView: Boolean = true) {
    if (!iconCustomizations.isEmpty()) {
      val actionManager = ActionManager.getInstance()
      for (actionId in iconCustomizations.keys) {
        val action = actionManager.getActionOrStub(actionId)
        if (action == null || action is ActionStub) {
          continue
        }

        initActionIcon(anAction = action, actionId = actionId, actionManager = actionManager)
        PresentationFactory.updatePresentation(action)
      }
    }
    if (updateView) {
      WindowManagerEx.getInstanceEx().getFrameHelper(null)?.updateView()
    }
  }

  @ApiStatus.Internal
  fun initActionIcon(anAction: AnAction, actionId: String, actionManager: ActionManager) {
    LOG.assertTrue(anAction !is ActionStub)
    val presentation = anAction.templatePresentation
    val originalIcon = presentation.icon
    if (presentation.getClientProperty(PROP_ORIGINAL_ICON) == null && anAction.isDefaultIcon && originalIcon != null) {
      presentation.putClientProperty(PROP_ORIGINAL_ICON, originalIcon)
    }

    val icon = iconCustomizations.get(actionId)?.let { CustomizationUtil.getIconForPath(actionManager, it) }
               ?: presentation.getClientProperty(PROP_ORIGINAL_ICON)
    presentation.icon = icon
    presentation.disabledIcon = if (icon == null) null else getDisabledIcon(icon)
    anAction.isDefaultIcon = icon == originalIcon
  }
}

private fun fillExtGroups(idToName: MutableMap<String, String>, extGroupIds: MutableSet<String>) {
  for (id in extGroupIds) {
    idToName.remove(id)
  }
  extGroupIds.clear()
  val extList = ArrayList<Pair<String, String>>()
  val registrar = CustomizableActionGroupRegistrar { groupId, groupTitle ->
    extList.add(groupId to groupTitle)
  }
  for (provider in EP_NAME.extensionList) {
    provider.registerGroups(registrar)
  }
  extList.sortWith { o1, o2 -> NaturalComparator.INSTANCE.compare(o1.second, o2.second) }
  for (couple in extList) {
    extGroupIds.add(couple.first)
    idToName.put(couple.first, couple.second)
  }
}

private object ActionUrlComparator : Comparator<ActionUrl> {
  const val DELETED: Int = 1

  private fun getEquivalenceClass(url: ActionUrl): Int {
    return when (url.actionType) {
      ActionUrl.DELETED -> 1
      ActionUrl.ADDED -> 2
      else -> 3
    }
  }

  override fun compare(u1: ActionUrl, u2: ActionUrl): Int {
    val w1 = getEquivalenceClass(u1)
    val w2 = getEquivalenceClass(u2)
    if (w1 != w2) {
      // deleted < added < others
      return w1 - w2
    }

    if (w1 == DELETED) {
      // within DELETED equivalence class urls with greater position go first
      return u2.absolutePosition - u1.absolutePosition
    }
    else {
      // within ADDED equivalence class: urls with lower position go first
      return u1.absolutePosition - u2.absolutePosition
    }
  }
}