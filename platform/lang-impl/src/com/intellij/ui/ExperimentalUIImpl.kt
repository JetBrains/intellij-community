// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.ide.ui.IconMapperBean
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.laf.LafManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.registry.Registry
import java.util.*
import javax.swing.UIManager.LookAndFeelInfo

/**
 * @author Konstantin Bulenkov
 */
class ExperimentalUIImpl : ExperimentalUI() {
  override fun getIconMappings() = loadIconMappingsImpl()

  override fun onExpUIEnabled() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) return

    setRegistryKeyIfNecessary("ide.experimental.ui", true)
    setRegistryKeyIfNecessary("debugger.new.tool.window.layout", true)
    UISettings.getInstance().openInPreviewTabIfPossible = true
    val name = if (JBColor.isBright()) "Light" else "Dark"
    val lafManager = LafManager.getInstance()
    val laf = lafManager.installedLookAndFeels.firstOrNull { x: LookAndFeelInfo -> x.name == name }
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
    ApplicationManager.getApplication().invokeLater({ RegistryBooleanOptionDescriptor.suggestRestart(null) }, ModalityState.NON_MODAL)
  }

  override fun onExpUIDisabled() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) return

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
    ApplicationManager.getApplication().invokeLater({ RegistryBooleanOptionDescriptor.suggestRestart(null) }, ModalityState.NON_MODAL)
  }

  fun setRegistryKeyIfNecessary(key: String, value: Boolean) {
    if (Registry.`is`(key) != value) {
      Registry.get(key).setValue(value)
    }
  }

  companion object {
    @JvmStatic
    fun loadIconMappingsImpl(): Map<ClassLoader, Map<String, String>> {
      val result = HashMap<ClassLoader, Map<String, String>>()
      IconMapperBean.EP_NAME.extensions.filterNotNull().forEach {
        val mappingFile = it.mappingFile
        val classLoader = it.pluginClassLoader
        if (classLoader != null) {
          val json = JSON.builder().enable().enable(JSON.Feature.READ_ONLY).build()
          try {
            val fin = Objects.requireNonNull(classLoader.getResource(mappingFile)).openStream()
            var map = result[classLoader]
            if (map == null) {
              map = mutableMapOf()
              result[classLoader] = map
            }
            readDataFromJson(json.mapFrom(fin), "", map as MutableMap)
          }
          catch (ignore: Exception) {
            System.err.println("Can't find $mappingFile")
          }
        }
      }
      return result
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
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