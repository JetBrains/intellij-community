// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.TabbedConfigurable
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableVisitor
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.navigation.Place
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.Component
import java.awt.Composite
import java.util.*
import java.util.function.Consumer
import java.util.function.Predicate

private val LOG = logger<ShowSettingsUtilImpl>()

// extended externally
open class ShowSettingsUtilImpl : ShowSettingsUtil() {
  companion object {
    @JvmStatic
    fun getDialog(project: Project?, groups: List<ConfigurableGroup>, toSelect: Configurable?): DialogWrapper {
      return SettingsDialogFactory.getInstance().create(
        currentOrDefaultProject(project),
        filterEmptyGroups(groups),
        toSelect,
        null
      )
    }

    /**
     * @param project         a project used to load project settings or `null`
     * @param withIdeSettings specifies whether to load application settings or not
     * @return an array with the root configurable group
     */
    @JvmStatic
    fun getConfigurableGroups(project: Project?, withIdeSettings: Boolean): Array<ConfigurableGroup> {
      val group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, withIdeSettings)
      return arrayOf(group)
    }

    /**
     * @param project         a project used to load project settings or `null`
     * @param withIdeSettings specifies whether to load application settings or not
     * @return all configurables as a plain list except the root configurable group
     */
    @JvmStatic
    fun getConfigurables(project: Project?, withIdeSettings: Boolean, checkNonDefaultProject: Boolean): List<Configurable> {
      val list: MutableList<Configurable> = ArrayList()
      for (configurable in ConfigurableExtensionPointUtil.getConfigurables(
        if (withIdeSettings) project else currentOrDefaultProject(project),
        withIdeSettings, checkNonDefaultProject
      )) {
        list.add(configurable)
        if (configurable is Configurable.Composite) {
          collect(list, (configurable as Configurable.Composite).configurables)
        }
      }
      return list
    }

    @JvmStatic
    fun showSettingsDialog(project: Project?, idToSelect: String?, filter: String?) {
      var group: ConfigurableGroup? = ConfigurableExtensionPointUtil.getConfigurableGroup(project,  /* withIdeSettings = */true)
      if (group!!.configurables.size == 0) {
        group = null
      }
      val configurableToSelect = if (idToSelect == null) null
      else ConfigurableVisitor.findById(idToSelect, listOf<ConfigurableGroup?>(group))
      SettingsDialogFactory.getInstance().create(
        currentOrDefaultProject(project), listOf<ConfigurableGroup?>(group),
        configurableToSelect,
        filter
      ).show()
    }

    @JvmStatic
    fun createDimensionKey(configurable: Configurable): String {
      return '#'.toString() + configurable.displayName.replace('\n', '_').replace(' ', '_')
    }
  }

  override fun showSettingsDialog(project: Project, vararg groups: ConfigurableGroup) {
    runCatching {
      getDialog(project, Arrays.asList(*groups), null).show()
    }.getOrLogException(LOG)
  }

  override fun <T : Configurable?> showSettingsDialog(project: Project?, configurableClass: Class<T>) {
    showSettingsDialog(project, configurableClass, null)
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
    getDialog(project, Arrays.asList(*groups), config).show()
  }

  override fun showSettingsDialog(project: Project?, nameToSelect: String) {
    val group = ConfigurableExtensionPointUtil.getConfigurableGroup(project,  /* withIdeSettings = */true)
    val groups = if (group.configurables.size == 0) emptyList() else listOf(group)
    getDialog(project, groups, findPreselectedByDisplayName(nameToSelect, groups)).show()
  }

  override fun showSettingsDialog(project: Project, toSelect: Configurable?) {
    val groups = listOf(ConfigurableExtensionPointUtil.getConfigurableGroup(project,  /* withIdeSettings = */true))
    getDialog(project, groups, toSelect).show()
  }

  override fun editConfigurable(project: Project, configurable: Configurable): Boolean {
    return editConfigurable(project, createDimensionKey(configurable), configurable)
  }

  override fun editConfigurable(project: Project, dimensionServiceKey: String, configurable: Configurable): Boolean {
    return editConfigurable(project, dimensionServiceKey, configurable, isWorthToShowApplyButton(configurable))
  }

  override fun editConfigurable(project: Project,
                                dimensionServiceKey: String,
                                configurable: Configurable,
                                showApplyButton: Boolean): Boolean {
    return editConfigurable(null, project, configurable, dimensionServiceKey, null, showApplyButton)
  }

  override fun editConfigurable(project: Project?, configurable: Configurable, advancedInitialization: Runnable?): Boolean {
    return editConfigurable(null, project, configurable, createDimensionKey(configurable), advancedInitialization,
                            isWorthToShowApplyButton(configurable))
  }

  override fun <T : Configurable> editConfigurable(project: Project?, configurable: T, advancedInitialization: Consumer<in T?>): Boolean {
    return editConfigurable(parent = null,
                            project = project,
                            configurable = configurable,
                            advancedInitialization = advancedInitialization,
                            dimensionKey = createDimensionKey(configurable),
                            showApplyButton = isWorthToShowApplyButton(configurable))
  }

  override fun editConfigurable(parent: Component?, configurable: Configurable): Boolean {
    return editConfigurable(parent, configurable, null)
  }

  override fun editConfigurable(parent: Component?, displayName: String): Boolean {
    return editConfigurable(parent, displayName, null as Runnable?)
  }

  override fun editConfigurable(parent: Component?, displayName: String, advancedInitialization: Runnable?): Boolean {
    val group = ConfigurableExtensionPointUtil.getConfigurableGroup(null,  /* withIdeSettings = */true)
    val groups = if (group.configurables.size == 0) emptyList() else listOf(group)
    val configurable = findPreselectedByDisplayName(displayName, groups)
    if (configurable == null) {
      LOG.error("Cannot find configurable for name [$displayName]")
      return false
    }
    return editConfigurable(parent, configurable, advancedInitialization)
  }

  override fun editConfigurable(parent: Component?, configurable: Configurable, advancedInitialization: Runnable?): Boolean {
    return editConfigurable(parent, null, configurable, createDimensionKey(configurable), advancedInitialization,
                            isWorthToShowApplyButton(configurable))
  }

  override fun editConfigurable(parent: Component, dimensionServiceKey: String, configurable: Configurable): Boolean {
    return editConfigurable(parent, null, configurable, dimensionServiceKey, null, isWorthToShowApplyButton(configurable))
  }
}

private fun collect(list: MutableList<in Configurable>, configurables: Array<Configurable>) {
  for (configurable in configurables) {
    list.add(configurable)
    if (configurable is Configurable.Composite) {
      collect(list, (configurable as Configurable.Composite).configurables)
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
  val groups: MutableList<ConfigurableGroup> = ArrayList()
  for (g in group) {
    if (g.configurables.size > 0) {
      groups.add(g)
    }
  }
  return groups
}

private fun isWorthToShowApplyButton(configurable: Configurable): Boolean {
  return configurable is Place.Navigator ||
         configurable is Composite ||
         configurable is TabbedConfigurable
}

private fun editConfigurable(parent: Component?,
                             project: Project?,
                             configurable: Configurable,
                             dimensionKey: String,
                             advancedInitialization: Runnable?,
                             showApplyButton: Boolean): Boolean {
  val consumer = if (advancedInitialization != null) Consumer { it: Configurable? -> advancedInitialization.run() } else null
  return editConfigurable(parent, project, configurable, consumer, dimensionKey, showApplyButton)
}

private fun <T : Configurable> editConfigurable(parent: Component?,
                                                 project: Project?,
                                                 configurable: T,
                                                 advancedInitialization: Consumer<in T?>?,
                                                 dimensionKey: String,
                                                 showApplyButton: Boolean): Boolean {
  val editor: DialogWrapper
  if (parent == null) {
    editor = SettingsDialogFactory.getInstance().create(project, dimensionKey, configurable, showApplyButton, false)
  }
  else {
    editor = SettingsDialogFactory.getInstance().create(parent, dimensionKey, configurable, showApplyButton, false)
  }
  if (advancedInitialization != null) {
    UiNotifyConnector.Once.installOn(editor.contentPane, object : Activatable {
      override fun showNotify() {
        advancedInitialization.accept(configurable)
      }
    })
  }
  return editor.showAndGet()
}