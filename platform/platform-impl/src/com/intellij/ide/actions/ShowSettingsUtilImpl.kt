// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.newEditor.settings.SettingsVirtualFileHolder
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.TabbedConfigurable
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.project.PROJECT_ID
import com.intellij.platform.project.ProjectId
import com.intellij.ui.navigation.Place
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.Component
import java.awt.Composite
import java.util.function.Consumer
import java.util.function.Predicate

private val LOG = logger<ShowSettingsUtilImpl>()

// extended externally
open class ShowSettingsUtilImpl : ShowSettingsUtil() {
  companion object {
    @JvmStatic
    @Deprecated("Use showSettings instead")
    fun getDialog(project: Project?, groups: List<ConfigurableGroup>, toSelect: Configurable?): DialogWrapper =
      createDialogWrapper(project, groups, toSelect)

    private fun createDialogWrapper(project: Project?, groups: List<ConfigurableGroup>, toSelect: Configurable?) =
      SettingsDialogFactory.getInstance().create(
        project = currentOrDefaultProject(project),
        groups = filterEmptyGroups(groups),
        configurable = toSelect,
        filter = null
      )

    @JvmStatic
    fun showSettings(project: Project?, groups: List<ConfigurableGroup>, toSelect: Configurable?) {
      showInternal(project) {
        createDialogWrapper(project, groups, toSelect) as SettingsDialog
      }
    }

    private fun showInternal(project: Project?, settingsDialogInitializer: () -> SettingsDialog) {
      if (AdvancedSettings.getBoolean("ide.ui.non.modal.settings.window")) {
        val currentOrDefaultProject = currentOrDefaultProject(project)
        runWithModalProgressBlocking(currentOrDefaultProject, "Opening settings") {
          val settingsFile = SettingsVirtualFileHolder.getInstance(currentOrDefaultProject).getOrCreate(settingsDialogInitializer);
          val fileEditorManager = FileEditorManager.getInstance(currentOrDefaultProject) as FileEditorManagerEx;
          val options = FileEditorOpenOptions(reuseOpen = true, isSingletonEditorInWindow = true)
          fileEditorManager.openFile(settingsFile, options);
        }
      }
      else {
        settingsDialogInitializer.invoke().show()
      }
    }

    /**
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
      return configurables(project = project, withIdeSettings = withIdeSettings, checkNonDefaultProject = checkNonDefaultProject).toList()
    }

    fun configurables(project: Project?, withIdeSettings: Boolean, checkNonDefaultProject: Boolean): Sequence<Configurable> {
      return sequence {
        for (configurable in ConfigurableExtensionPointUtil.getConfigurables(
          if (withIdeSettings) project else currentOrDefaultProject(project),
          withIdeSettings,
          checkNonDefaultProject,
        )) {
          yield(configurable)
          if (configurable is Configurable.Composite) {
            collect(configurable.configurables)
          }
        }
      }
    }

    @JvmStatic
    fun showSettingsDialog(project: Project?, idToSelect: String?, filter: String?) {
      val group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, /* withIdeSettings = */true)
        .takeIf { !it.configurables.isEmpty() }
      val configurableToSelect = if (idToSelect == null) null else ConfigurableVisitor.findById(idToSelect, listOf(group))

      val currentOrDefaultProject = currentOrDefaultProject(project)
      showInternal(currentOrDefaultProject) {
        SettingsDialog(currentOrDefaultProject,
                       listOf<ConfigurableGroup>(group!!),
                       configurableToSelect,
                       filter)
      }
    }

    @JvmStatic
    fun createDimensionKey(configurable: Configurable): String {
      return '#'.toString() + configurable.displayName.replace('\n', '_').replace(' ', '_')
    }
  }

  override fun showSettingsDialog(project: Project, vararg groups: ConfigurableGroup) {
    runCatching {
      showSettings(project, groups.asList(), null)
    }.getOrLogException(LOG)
  }

  override fun <T : Configurable?> showSettingsDialog(project: Project?, configurableClass: Class<T>) {
    showSettingsDialog(project = project, configurableClass = configurableClass, additionalConfiguration = null)
  }

  override fun <T : Configurable?> showSettingsDialog(project: Project?,
                                                      configurableClass: Class<T>,
                                                      additionalConfiguration: Consumer<in T>?) {
    assert(Configurable::class.java.isAssignableFrom(configurableClass)) { "Not a configurable: " + configurableClass.name }
    showSettingsDialog(project, { it: Configurable? -> ConfigurableWrapper.cast(configurableClass, it) != null }) { it: Configurable ->
      if (additionalConfiguration != null) {
        val toConfigure = ConfigurableWrapper.cast(configurableClass, it) ?: error("Wrong configurable found: " + it.javaClass)
        additionalConfiguration.accept(toConfigure)
      }
    }
  }

  override fun showSettingsDialog(project: Project?,
                                  predicate: Predicate<in Configurable>,
                                  additionalConfiguration: Consumer<in Configurable>?) {
    val groups = getConfigurableGroups(project, true)
    val config = object : ConfigurableVisitor() {
      override fun accept(configurable: Configurable): Boolean {
        return predicate.test(configurable)
      }
    }.find(*groups) ?: error("Cannot find configurable for specified predicate")
    additionalConfiguration?.accept(config)
    showSettings(project, groups.asList(), config)
  }

  override fun showSettingsDialog(project: Project?, nameToSelect: String) {
    val group = ConfigurableExtensionPointUtil.getConfigurableGroup(project,  /* withIdeSettings = */true)
    val groups = if (group.configurables.isEmpty()) emptyList() else listOf(group)
    showSettings(project = project, groups = groups, toSelect = findPreselectedByDisplayName(nameToSelect, groups))
  }

  override fun showSettingsDialog(project: Project, toSelect: Configurable?) {
    val groups = listOf(ConfigurableExtensionPointUtil.getConfigurableGroup(project,  /* withIdeSettings = */true))
    showSettings(project = project, groups = groups, toSelect = toSelect)
  }

  override fun editConfigurable(project: Project, configurable: Configurable): Boolean {
    return editConfigurable(project = project, dimensionServiceKey = createDimensionKey(configurable), configurable = configurable)
  }

  override fun editConfigurable(project: Project, dimensionServiceKey: String, configurable: Configurable): Boolean {
    return editConfigurable(project = project,
                            dimensionServiceKey = dimensionServiceKey,
                            configurable = configurable,
                            showApplyButton = isWorthToShowApplyButton(configurable))
  }

  override fun editConfigurable(project: Project,
                                dimensionServiceKey: String,
                                configurable: Configurable,
                                showApplyButton: Boolean): Boolean {
    return editConfigurable(parent = null,
                            project = project,
                            configurable = configurable,
                            dimensionKey = dimensionServiceKey,
                            advancedInitialization = null,
                            showApplyButton = showApplyButton)
  }

  override fun editConfigurable(project: Project?, configurable: Configurable, advancedInitialization: Runnable?): Boolean {
    return editConfigurable(parent = null,
                            project = project,
                            configurable = configurable,
                            dimensionKey = createDimensionKey(configurable),
                            advancedInitialization = advancedInitialization?.let { { it.run() } },
                            showApplyButton = isWorthToShowApplyButton(configurable))
  }

  override fun <T : Configurable> editConfigurable(project: Project?, configurable: T, advancedInitialization: Consumer<in T>): Boolean {
    return editConfigurable(parent = null,
                            project = project,
                            configurable = configurable,
                            advancedInitialization = { c: T -> advancedInitialization.accept(c) },
                            dimensionKey = createDimensionKey(configurable),
                            showApplyButton = isWorthToShowApplyButton(configurable))
  }

  override fun editConfigurable(parent: Component?, configurable: Configurable): Boolean {
    return editConfigurable(parent = parent, configurable = configurable, advancedInitialization = null)
  }

  override fun editConfigurable(parent: Component?, displayName: String): Boolean {
    return editConfigurable(parent = parent, displayName = displayName, advancedInitialization = null as Runnable?)
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
    return editConfigurable(parent = parent,
                            project = null,
                            configurable = configurable,
                            dimensionKey = createDimensionKey(configurable),
                            advancedInitialization = advancedInitialization?.let { { it.run() } },
                            showApplyButton = isWorthToShowApplyButton(configurable))
  }

  override fun editConfigurable(parent: Component, dimensionServiceKey: String, configurable: Configurable): Boolean {
    return editConfigurable(parent = parent,
                            project = null,
                            configurable = configurable,
                            dimensionKey = dimensionServiceKey,
                            advancedInitialization = null,
                            showApplyButton = isWorthToShowApplyButton(configurable))
  }
}

private suspend fun SequenceScope<Configurable>.collect(configurables: Array<Configurable>) {
  for (configurable in configurables) {
    yield(configurable)
    if (configurable is Configurable.Composite) {
      collect(configurables = (configurable as Configurable.Composite).configurables)
    }
  }
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

private fun filterEmptyGroups(group: List<ConfigurableGroup>): List<ConfigurableGroup> {
  return group.filter { it.configurables.isNotEmpty() }
}

private fun isWorthToShowApplyButton(configurable: Configurable): Boolean {
  return configurable is Place.Navigator || configurable is Composite || configurable is TabbedConfigurable
}

private fun editConfigurable(parent: Component?,
                             project: Project?,
                             configurable: Configurable,
                             dimensionKey: String,
                             advancedInitialization: (() -> Unit)?,
                             showApplyButton: Boolean): Boolean {
  val consumer = if (advancedInitialization == null) null else { _: Configurable? -> advancedInitialization() }
  return editConfigurable(parent = parent,
                          project = project,
                          configurable = configurable,
                          advancedInitialization = consumer,
                          dimensionKey = dimensionKey,
                          showApplyButton = showApplyButton)
}

private fun <T : Configurable> editConfigurable(parent: Component?,
                                                 project: Project?,
                                                 configurable: T,
                                                 advancedInitialization: ((T) -> Unit)?,
                                                 dimensionKey: String,
                                                 showApplyButton: Boolean): Boolean {
  val editor = if (parent == null) {
    SettingsDialogFactory.getInstance().create(project = project,
                                               key = dimensionKey,
                                               configurable = configurable,
                                               showApplyButton = showApplyButton,
                                               showResetButton = false)
  }
  else {
    SettingsDialogFactory.getInstance().create(parent = parent,
                                               key = dimensionKey,
                                               configurable = configurable,
                                               showApplyButton = showApplyButton,
                                               showResetButton = false)
  }
  if (advancedInitialization != null) {
    UiNotifyConnector.Once.installOn(editor.contentPane, object : Activatable {
      override fun showNotify() {
        advancedInitialization(configurable)
      }
    })
  }
  return editor.showAndGet()
}