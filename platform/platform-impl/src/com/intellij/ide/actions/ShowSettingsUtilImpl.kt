// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.startup.StartupManagerEx
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.NonModalSettingsPolicy
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.TabbedConfigurable
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import com.intellij.openapi.options.newEditor.SettingsDialogPerformanceTracker
import com.intellij.openapi.options.newEditor.SettingsNonModalDialog
import com.intellij.openapi.options.newEditor.SettingsNonModalDialogFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapperDialog
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.ui.navigation.Place
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import java.awt.Component
import java.awt.Composite
import java.util.function.Consumer
import java.util.function.Predicate
import javax.swing.SwingUtilities

private val LOG = logger<ShowSettingsUtilImpl>()

// extended externally
open class ShowSettingsUtilImpl : ShowSettingsUtil() {
  companion object {
    @JvmStatic
    @Deprecated("Use showSettings instead")
    fun getDialog(project: Project?, groups: List<ConfigurableGroup>, toSelect: Configurable?): DialogWrapper {
      return createDialogWrapper(project, groups, toSelect, filter = null)
    }

    @JvmStatic
    fun showSettings(project: Project?, groups: List<ConfigurableGroup>, toSelect: Configurable?) {
      (getInstance() as ShowSettingsUtilImpl).doShow(project, groups, toSelect, filter = null)
    }

    /**
     * Builds the whole configurable tree. **This is a heavy operation**, mostly class loading, so it
     * must not run on the UI thread.
     *
     * Do not call this to open the settings dialog. Use `showSettingsDialog` with an id, or
     * `ShowSettingsUtilEx.showSettingsDialog`, which reuses an open settings window and builds no tree.
     * Do not call this to test whether a page exists either.
     *
     * @param project         a project used to load project settings or `null`
     * @param withIdeSettings specifies whether to load application settings or not
     * @return an array with the root-configurable group
     */
    @JvmStatic
    fun getConfigurableGroups(project: Project?, withIdeSettings: Boolean): Array<ConfigurableGroup> {
      return arrayOf(ConfigurableExtensionPointUtil.getConfigurableGroup(project, withIdeSettings))
    }

    /**
     * @param project         a project used to load project settings or `null`
     * @param withIdeSettings specifies whether to load application settings or not
     * @return all configurables as a plain list except the root configurable group
     */
    @JvmStatic
    fun getConfigurables(project: Project?, withIdeSettings: Boolean, checkNonDefaultProject: Boolean): List<Configurable> {
      return configurables(project, withIdeSettings, checkNonDefaultProject).toList()
    }

    fun configurables(project: Project?, withIdeSettings: Boolean, checkNonDefaultProject: Boolean): Sequence<Configurable> {
      suspend fun SequenceScope<Configurable>.collect(configurables: Array<Configurable>) {
        for (configurable in configurables) {
          yield(configurable)
          if (configurable is Configurable.Composite) {
            val configurables = runCatching {
              (configurable as Configurable.Composite).configurables
            }.getOrLogException(LOG)

            configurables?.let {
              collect(configurables = configurables)
            }
          }
        }
      }

      return sequence {
        val project = if (withIdeSettings) project else currentOrDefaultProject(project)
        for (configurable in ConfigurableExtensionPointUtil.getConfigurables(project, withIdeSettings, checkNonDefaultProject)) {
          yield(configurable)
          if (configurable is Configurable.Composite) {
            val configurables = runCatching {
              configurable.configurables
            }.getOrLogException(LOG)

            configurables?.let {
              collect(configurables)
            }
          }
        }
      }
    }

    @JvmStatic
    fun showSettingsDialog(project: Project?, idToSelect: String?, filter: String?) {
      (getInstance() as ShowSettingsUtilImpl).showSettingsDialogLazily(project, filter) { groups ->
        if (idToSelect == null) null else ConfigurableVisitor.findById(idToSelect, groups)
      }
    }

    @JvmStatic
    fun createDimensionKey(configurable: Configurable): String {
      return '#'.toString() + configurable.displayName.replace('\n', '_').replace(' ', '_')
    }
  }

  /**
   * Shows the settings dialog, and builds the configurable tree only when a dialog must be created.
   *
   * An open non-modal window of the same project holds its own tree, so it selects [toSelect] there.
   * A tree that is built for such a call is discarded, which costs a full tree build on every repeat open.
   *
   * [toSelect] receives the groups that the dialog uses, because the tree selects a node by the
   * [Configurable] instance.
   */
  @ApiStatus.Internal
  fun showSettingsDialogLazily(
    project: Project?,
    filter: String?,
    toSelect: (List<ConfigurableGroup>) -> Configurable?,
  ) {
    SettingsDialogPerformanceTracker.markOpeningStarted()
    doShow(project, { buildConfigurableGroups(project) }, toSelect, filter)
  }

  private fun buildConfigurableGroups(project: Project?): List<ConfigurableGroup> {
    return listOf(ConfigurableExtensionPointUtil.getConfigurableGroup(project, /* withIdeSettings = */true))
  }

  private fun isModalRequired(project: Project?): Boolean {
    return !(project != null &&
             project != ProjectManager.getInstance().defaultProject &&
             NonModalSettingsPolicy.isNonModalSettingsEnabledByAllPolicies() &&
             ModalityState.current() == ModalityState.nonModal())
  }

  @ApiStatus.Internal
  fun doShow(project: Project?, groups: List<ConfigurableGroup>, toSelect: Configurable?, filter: String?) {
    doShow(project, { groups }, { toSelect }, filter)
  }

  /**
   * Shows the settings dialog, and builds the configurable tree only when a dialog must be created.
   *
   * An open non-modal window of the same project holds its own tree, so [groups] is not called for
   * such a call. [toSelect] receives the groups that the dialog shows, because the tree selects a
   * node by the [Configurable] instance.
   */
  @ApiStatus.Internal
  protected open fun doShow(
    project: Project?,
    groups: () -> List<ConfigurableGroup>,
    toSelect: (List<ConfigurableGroup>) -> Configurable?,
    filter: String?,
  ) {
    val isModal = isModalRequired(project)

    if (!isModal) {
      SettingsNonModalDialogFactory.getInstance().show(project!!, { filterEmptyGroups(groups()) }, toSelect, filter)
    } else {
      val filteredGroups = filterEmptyGroups(groups())
      createDialogWrapper(project, filteredGroups, toSelect(filteredGroups), filter).show()
    }
  }

  override fun showSettingsDialog(project: Project, vararg groups: ConfigurableGroup) {
    runCatching {
      doShow(project, groups.asList(), toSelect = null, filter = null)
    }.getOrLogException(LOG)
  }

  @ApiStatus.Internal
  override suspend fun showSettingsDialog(project: Project) {
    // We want to ensure that clients don’t simply replace one API with another,
    // but actually rework the invocation to be performed not in EDT.
    ThreadingAssertions.assertBackgroundThread()/**/
    SettingsDialogPerformanceTracker.markOpeningStarted()

    // an open window of the same project already holds a tree, so no tree is built for this call
    if (withContext(Dispatchers.EDT) { SettingsNonModalDialog.navigateOpenDialog(project, null) { null } }) {
      return
    }

    showSettingsDialog(project, buildConfigurableGroups(project))
  }

  @ApiStatus.Internal
  override suspend fun showSettingsDialog(project: Project, groups: List<ConfigurableGroup>) {
    // We want to ensure that clients don’t simply replace one API with another,
    // but actually rework the invocation to be performed not in EDT.
    ThreadingAssertions.assertBackgroundThread()
 
    val isModal = project.isDefault || !NonModalSettingsPolicy.isNonModalSettingsEnabledByAllPolicies()
    // the filter reads `configurables` of every group, so it stays on the background thread
    val filteredGroups = filterEmptyGroups(groups)
    withContext(Dispatchers.EDT) {
      if (!isModal) {
        SettingsNonModalDialogFactory.getInstance().show(project, { filteredGroups }, { null }, null)
      } else {
        val settingsDialogFactory = serviceAsync<SettingsDialogFactory>()
        settingsDialogFactory.create(project, filteredGroups, null, null).show()
      }
    }
  }

  override fun <T : Configurable?> showSettingsDialog(project: Project?, configurableClass: Class<T>) {
    showSettingsDialog(project = project, configurableClass = configurableClass, additionalConfiguration = null)
  }

  override fun <T : Configurable?> showSettingsDialog(
    project: Project?,
    configurableClass: Class<T>,
    additionalConfiguration: Consumer<in T>?,
  ) {
    assert(Configurable::class.java.isAssignableFrom(configurableClass)) { "Not a configurable: " + configurableClass.name }
    showSettingsDialog(project, { it: Configurable? -> ConfigurableWrapper.tryToCast(configurableClass, it) }) { it: Configurable ->
      if (additionalConfiguration != null) {
        val toConfigure = ConfigurableWrapper.cast(configurableClass, it)
                          ?: error("Wrong configurable found: " + it.javaClass + " but expected: " + configurableClass)
        additionalConfiguration.accept(toConfigure)
      }
    }
  }

  override fun showSettingsDialog(
    project: Project?,
    predicate: Predicate<in Configurable>,
    additionalConfiguration: Consumer<in Configurable>?,
  ) {
    showSettingsDialogLazily(project, filter = null) { groups ->
      val config = ConfigurableVisitor.find(predicate, groups) ?: error("Cannot find configurable for specified predicate")
      additionalConfiguration?.accept(config)
      config
    }
  }

  override fun showSettingsDialog(project: Project?, nameToSelect: String) {
    showSettingsDialogLazily(project, filter = null) { groups -> findPreselectedByDisplayName(nameToSelect, groups) }
  }

  override fun showSettingsDialog(project: Project, toSelect: Configurable?) {
    // the id keeps the target findable in any tree, so an open dialog needs no new one
    val id = (toSelect as? SearchableConfigurable)?.id
    if (id == null) {
      val groups = listOf(ConfigurableExtensionPointUtil.getConfigurableGroup(project,  /* withIdeSettings = */true))
      showSettings(project, groups, toSelect)
      return
    }
    showSettingsDialogLazily(project, filter = null) { groups -> ConfigurableVisitor.findById(id, groups) ?: toSelect }
  }

  override fun editConfigurable(project: Project?, configurable: Configurable): Boolean {
    return editConfigurable(project, createDimensionKey(configurable), configurable)
  }

  override fun editConfigurable(project: Project?, dimensionServiceKey: String, configurable: Configurable): Boolean {
    return editConfigurable(project, dimensionServiceKey, configurable, isWorthToShowApplyButton(configurable))
  }

  override fun editConfigurable(
    project: Project?,
    dimensionServiceKey: String,
    configurable: Configurable,
    showApplyButton: Boolean,
  ): Boolean {
    return editConfigurable(parent = null, project, configurable, dimensionServiceKey, advancedInitialization = null, showApplyButton)
  }

  override fun editConfigurable(project: Project?, configurable: Configurable, advancedInitialization: Runnable?): Boolean {
    return editConfigurable(
      parent = null,
      project = project,
      configurable = configurable,
      dimensionKey = createDimensionKey(configurable),
      advancedInitialization = advancedInitialization?.let { { it.run() } },
      showApplyButton = isWorthToShowApplyButton(configurable),
    )
  }

  override fun <T : Configurable> editConfigurable(project: Project?, configurable: T, advancedInitialization: Consumer<in T>): Boolean {
    return editConfigurable(
      parent = null,
      project = project,
      configurable = configurable,
      advancedInitialization = { c: T -> advancedInitialization.accept(c) },
      dimensionKey = createDimensionKey(configurable),
      showApplyButton = isWorthToShowApplyButton(configurable),
    )
  }

  override fun editConfigurable(parent: Component?, configurable: Configurable): Boolean {
    return editConfigurable(parent, configurable, advancedInitialization = null)
  }

  override fun editConfigurable(parent: Component?, displayName: String): Boolean {
    return editConfigurable(parent, displayName, advancedInitialization = null as Runnable?)
  }

  override fun editConfigurable(parent: Component?, displayName: String, advancedInitialization: Runnable?): Boolean {
    val group = ConfigurableExtensionPointUtil.getConfigurableGroup(null, /* withIdeSettings = */true)
    val groups = if (group.configurables.isEmpty()) emptyList() else listOf(group)
    val configurable = findPreselectedByDisplayName(displayName, groups)
    if (configurable == null) {
      LOG.error("Cannot find configurable for name [$displayName]")
      return false
    }
    return editConfigurable(parent, configurable, advancedInitialization)
  }

  override fun editConfigurable(parent: Component?, configurable: Configurable, advancedInitialization: Runnable?): Boolean {
    return editConfigurable(
      parent = parent,
      project = null,
      configurable = configurable,
      dimensionKey = createDimensionKey(configurable),
      advancedInitialization = advancedInitialization?.let { { it.run() } },
      showApplyButton = isWorthToShowApplyButton(configurable),
    )
  }

  override fun editConfigurable(parent: Component, dimensionServiceKey: String, configurable: Configurable): Boolean {
    return editConfigurable(parent, project = null, configurable, dimensionServiceKey, advancedInitialization = null, isWorthToShowApplyButton(configurable))
  }

  override fun closeSettings(@NotNull project: Project, @NotNull component: Component) {
    val dialogWrapper = getDialogWrapperFor(component) ?: return
    dialogWrapper.doCancelAction()
  }
}

private fun getDialogWrapperFor(component: Component): DialogWrapper? {
  val window = UIUtil.getWindow(component)
  return (window as? DialogWrapperDialog)?.dialogWrapper
}

private fun createDialogWrapper(
  project: Project?,
  groups: List<ConfigurableGroup>,
  toSelect: Configurable?,
  filter: String?,
): DialogWrapper {
  val project = currentOrDefaultProject(project)
  // Note: groups should already be filtered by the caller
  return SettingsDialogFactory.getInstance().create(project, groups, toSelect, filter)
}

private fun findPreselectedByDisplayName(preselectedConfigurableDisplayName: String, groups: List<ConfigurableGroup>): Configurable? {
  for (eachGroup in groups) {
    for (configurable in SearchUtil.expandGroup(eachGroup)) {
      if (preselectedConfigurableDisplayName == configurable.displayName) {
        return configurable
      }
    }
  }
  return null
}

/**
 * Drops a group that holds no configurable.
 *
 * This reads `configurables` of every group, so it builds the tree when it is not built yet.
 */
private fun filterEmptyGroups(group: List<ConfigurableGroup>): List<ConfigurableGroup> {
  return group.filter { it.configurables.isNotEmpty() }
}

private fun isWorthToShowApplyButton(configurable: Configurable): Boolean {
  return configurable is Place.Navigator || configurable is Composite || configurable is TabbedConfigurable
}

private fun editConfigurable(
  parent: Component?,
  project: Project?,
  configurable: Configurable,
  dimensionKey: String,
  advancedInitialization: (() -> Unit)?,
  showApplyButton: Boolean,
): Boolean {
  val advancedInitialization = if (advancedInitialization == null) null else { _: Configurable? -> advancedInitialization() }
  return editConfigurable(parent, project, configurable, advancedInitialization, dimensionKey, showApplyButton)
}

private fun <T : Configurable> editConfigurable(
  parent: Component?,
  project: Project?,
  configurable: T,
  advancedInitialization: ((T) -> Unit)?,
  dimensionKey: String,
  showApplyButton: Boolean,
): Boolean {
  val editor = if (parent == null) {
    SettingsDialogFactory.getInstance().create(project, dimensionKey, configurable, showApplyButton, showResetButton = false)
  }
  else {
    SettingsDialogFactory.getInstance().create(parent, dimensionKey, configurable, showApplyButton, showResetButton = false)
  }
  if (advancedInitialization != null) {
    @Suppress("UsagesOfObsoleteApi")
    UiNotifyConnector.Once.installOn(editor.contentPane, object : Activatable {
      override fun showNotify() {
        advancedInitialization(configurable)
      }
    })
  }
  return editor.showAndGet()
}

internal fun scheduleDoShowSettingsDialogWithACheckThatProjectIsInitialized(project: Project) {
  SettingsDialogPerformanceTracker.markOpeningStarted()

  project.service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
    launch {
      (serviceAsync<SearchableOptionsRegistrar>() as? SearchableOptionsRegistrarImpl)?.initialize()
    }

    // an open window of the same project already holds a tree, so no tree is built for this call
    if (withContext(Dispatchers.EDT) { SettingsNonModalDialog.navigateOpenDialog(project, null) { null } }) {
      return@launch
    }

    if (!project.isDefault) {
      (project.serviceAsync<StartupManager>() as StartupManagerEx).waitForInitProjectActivities(IdeBundle.message("settings.modal.opening.message"))
    }
    serviceAsync<ShowSettingsUtil>().showSettingsDialog(project)

    if (LOG.isDebugEnabled()) {
      val startTime = System.nanoTime()
      // SwingUtilities must be used here
      SwingUtilities.invokeLater {
        val endTime = System.nanoTime()
        LOG.debug { "Displaying settings dialog took ${(endTime - startTime) / 1_000_000} ms" }
      }
    }
  }
}
