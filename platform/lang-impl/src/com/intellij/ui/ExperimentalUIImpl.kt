// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.feedback.new_ui.state.NewUIInfoService
import com.intellij.ide.ui.IconMapperBean
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.laf.LafManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ResourceUtil

/**
 * @author Konstantin Bulenkov
 */
class ExperimentalUIImpl : ExperimentalUI() {
  override fun getIconMappings() = loadIconMappingsImpl()

  override fun onExpUIEnabled(suggestRestart: Boolean) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) return

    NewUIInfoService.getInstance().updateEnableNewUIDate()
    
    setRegistryKeyIfNecessary("ide.experimental.ui", true)
    setRegistryKeyIfNecessary("debugger.new.tool.window.layout", true)
    UISettings.getInstance().openInPreviewTabIfPossible = true
    UISettings.getInstance().hideToolStripes = false
    val name = if (JBColor.isBright()) "Light" else "Dark"
    val lafManager = LafManager.getInstance()
    val laf = lafManager.installedLookAndFeels.firstOrNull { it.name == name }
    if (laf != null) {
      lafManager.currentLookAndFeel = laf
      if (lafManager.autodetect) {
        if (JBColor.isBright()) {
          lafManager.setPreferredLightLaf(laf)
        }
        else {
          lafManager.setPreferredDarkLaf(laf)
        }
      }
    }
    if (suggestRestart) {
      ApplicationManager.getApplication().invokeLater({ RegistryBooleanOptionDescriptor.suggestRestart(null) }, ModalityState.NON_MODAL)
    }
  }

  override fun onExpUIDisabled(suggestRestart: Boolean) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) return

    NewUIInfoService.getInstance().updateDisableNewUIDate()
    
    setRegistryKeyIfNecessary("ide.experimental.ui", false)
    setRegistryKeyIfNecessary("debugger.new.tool.window.layout", false)
    val mgr = LafManager.getInstance() as LafManagerImpl
    val currentLafName = mgr.currentLookAndFeel?.name
    if (currentLafName == "Dark" || currentLafName == "Light") {
      val laf = if (JBColor.isBright()) mgr.defaultLightLaf else mgr.defaultDarkLaf
      mgr.setCurrentLookAndFeel(laf)
      if (mgr.autodetect) {
        if (JBColor.isBright()) {
          mgr.setPreferredLightLaf(laf)
        }
        else {
          mgr.setPreferredDarkLaf(laf)
        }
      }
    }
    if (suggestRestart) {
      ApplicationManager.getApplication().invokeLater({ RegistryBooleanOptionDescriptor.suggestRestart(null) }, ModalityState.NON_MODAL)
    }
  }

  companion object {
    fun loadIconMappingsImpl(): Map<ClassLoader, Map<String, String>> {
      val extensions = IconMapperBean.EP_NAME.extensionList
      if (extensions.isEmpty()) {
        return emptyMap()
      }

      val result = HashMap<ClassLoader, MutableMap<String, String>>()
      val json = JSON.builder().enable().enable(JSON.Feature.READ_ONLY).build()
      for (iconMapper in extensions) {
        val mappingFile = iconMapper.mappingFile
        val classLoader = iconMapper.pluginClassLoader ?: continue
        try {
          val data = ResourceUtil.getResourceAsBytes(mappingFile, classLoader)!!
          val map = result.computeIfAbsent(classLoader) { HashMap() }
          readDataFromJson(json.mapFrom(data), "", map)
        }
        catch (ignore: Exception) {
          logger<ExperimentalUIImpl>().warn("Can't find $mappingFile")
        }
      }
      return result
    }

    private fun setRegistryKeyIfNecessary(key: String, value: Boolean) {
      if (Registry.`is`(key) != value) {
        Registry.get(key).setValue(value)
      }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readDataFromJson(json: Map<String, Any>, prefix: String, result: MutableMap<String, String>) {
      json.forEach { (key, value) ->
        when (value) {
          is String -> result[value] = prefix + key
          is Map<*, *> -> readDataFromJson(value as Map<String, Any>, "$prefix$key/", result)
          is List<*> -> value.forEach { result[it as String] = "$prefix$key" }
        }
      }
    }
  }
}