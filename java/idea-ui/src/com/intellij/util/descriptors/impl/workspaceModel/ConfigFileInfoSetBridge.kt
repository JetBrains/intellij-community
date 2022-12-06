// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.descriptors.impl.workspaceModel

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.MultiMap
import com.intellij.util.descriptors.*
import com.intellij.util.descriptors.impl.ConfigFileContainerImpl
import com.intellij.util.descriptors.impl.ConfigFileImpl
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.util.*

class ConfigFileInfoSetBridge(private val myMetaDataProvider: ConfigFileMetaDataProvider) : ConfigFileInfoSet {
  private val configFiles = MultiMap<ConfigFileMetaData, ConfigFileInfo>()
  private var myContainer: ConfigFileContainerImpl? = null
  override fun addConfigFile(descriptor: ConfigFileInfo) {
    configFiles.putValue(descriptor.metaData, descriptor)
    onChange()
  }

  override fun addConfigFile(metaData: ConfigFileMetaData, url: String) {
    addConfigFile(ConfigFileInfo(metaData, url))
  }

  override fun removeConfigFile(descriptor: ConfigFileInfo) {
    configFiles.remove(descriptor.metaData, descriptor)
    onChange()
  }

  override fun replaceConfigFile(metaData: ConfigFileMetaData, newUrl: String) {
    configFiles.remove(metaData)
    addConfigFile(ConfigFileInfo(metaData, newUrl))
  }

  override fun updateConfigFile(configFile: ConfigFile) {
    configFiles.remove(configFile.metaData, configFile.info)
    val info = ConfigFileInfo(configFile.metaData, configFile.url)
    configFiles.putValue(info.metaData, info)
    (configFile as ConfigFileImpl).info = info
  }

  override fun removeConfigFiles(vararg metaData: ConfigFileMetaData) {
    for (data in metaData) {
      configFiles.remove(data)
    }
    onChange()
  }

  override fun getConfigFileInfo(metaData: ConfigFileMetaData): ConfigFileInfo? {
    val descriptors = configFiles[metaData]
    return if (descriptors.isEmpty()) null else descriptors.iterator().next()
  }

  override fun getConfigFileInfos(): List<ConfigFileInfo> {
    return java.util.List.copyOf(configFiles.values())
  }

  override fun setConfigFileInfos(descriptors: Collection<ConfigFileInfo>) {
    configFiles.clear()
    for (descriptor in descriptors) {
      configFiles.putValue(descriptor.metaData, descriptor)
    }
    onChange()
  }

  private fun onChange() {
  }

  override fun getMetaDataProvider(): ConfigFileMetaDataProvider {
    return myMetaDataProvider
  }

  @Deprecated("Deprecated in Java")
  override fun readExternal(element: Element) {
    error("Unused if working via bridge for spring facet")
  }

  @Deprecated("Deprecated in Java")
  override fun writeExternal(element: Element) {
    error("Unused if working via bridge for spring facet")
  }

  override fun setContainer(container: ConfigFileContainer) {
    LOG.assertTrue(myContainer == null)
    myContainer = container as ConfigFileContainerImpl
    myContainer!!.updateDescriptors(configFiles)
  }

  companion object {
    private val LOG = Logger.getInstance(ConfigFileInfoSetBridge::class.java)

    @NonNls
    val ELEMENT_NAME = "deploymentDescriptor"

    @NonNls
    val ID_ATTRIBUTE = "name"

    @NonNls
    val URL_ATTRIBUTE = "url"
  }
}
